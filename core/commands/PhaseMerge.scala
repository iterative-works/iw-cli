// PURPOSE: Phase-merge command logic: polls CI, optionally invokes recovery, merges, advances
// PURPOSE: All I/O goes through CommandEnv (clock + tracker + hooks + git + reviewState)

package iw.core.commands

import iw.core.model.{PhaseMerge as PhaseMergeModel, *}

object PhaseMerge:
  private case class Resolved(
      timeoutMs: Long,
      pollIntervalMs: Long,
      maxRetries: Int,
      issueId: IssueId,
      phaseNumber: PhaseNumber,
      featureBranch: String,
      phaseBranchName: String,
      forgeType: ForgeType,
      gitlabHost: Option[String],
      repository: String,
      prUrl: String,
      prNumber: Int
  )

  def run(args: Seq[String], env: CommandEnv): CommandResult =
    val argList = args.toList
    parseArgs(argList) match
      case Left(err) =>
        env.console.err(s"Error: $err")
        CommandResult.error
      case Right(durations) => resolveAndExecute(argList, durations, env)

  private final case class Durations(
      timeoutMs: Long,
      pollIntervalMs: Long,
      maxRetries: Int
  )

  private def parseArgs(argList: List[String]): Either[String, Durations] =
    val timeoutStr = PhaseArgs.namedArg(argList, "--timeout").getOrElse("30m")
    val pollStr =
      PhaseArgs.namedArg(argList, "--poll-interval").getOrElse("30s")
    val maxStr = PhaseArgs.namedArg(argList, "--max-retries").getOrElse("2")
    for
      t <- PhaseMergeArgs
        .parseDurationArg(timeoutStr, "--timeout")
      p <- PhaseMergeArgs
        .parseDurationArg(pollStr, "--poll-interval")
      m <- PhaseMergeArgs
        .parseMaxRetries(maxStr)
    yield Durations(t, p, m)

  private def resolveAndExecute(
      argList: List[String],
      d: Durations,
      env: CommandEnv
  ): CommandResult =
    val outcome = for
      currentBranch <- env.git.getCurrentBranch(env.cwd)
      branchInfo <- currentBranch match
        case PhaseBranch(fb, pn) => Right((fb, currentBranch, pn))
        case _                   =>
          Left(
            s"Not on a phase sub-branch (current branch: '$currentBranch'). Run 'iw phase-start' first."
          )
      (featureBranch, phaseBranchName, phaseNumRaw) = branchInfo
      issueId <- PhaseArgs.resolveIssueId(
        PhaseArgs.namedArg(argList, "--issue-id"),
        featureBranch
      )
      phaseNumber <- PhaseArgs.resolvePhaseNumber(
        PhaseArgs.namedArg(argList, "--phase-number"),
        phaseNumRaw
      )
      cfgAndForge <- readConfigAndForge(env)
      (config, forgeType) = cfgAndForge
      remoteOpt = env.git.getRemoteUrl(env.cwd)
      gitlabHost = remoteOpt.flatMap(_.host.toOption)
      repository <- resolveRepository(config, remoteOpt)
      reviewPath = env.cwd / "project-management" / "issues" / issueId.value /
        "review-state.json"
      prUrl <- env.reviewState.readPrUrl(reviewPath)
      _ <- validatePrUrl(forgeType, repository, prUrl)
      prNumber <- PhaseMergeModel.extractPrNumber(prUrl)
    yield Resolved(
      timeoutMs = d.timeoutMs,
      pollIntervalMs = d.pollIntervalMs,
      maxRetries = d.maxRetries,
      issueId = issueId,
      phaseNumber = phaseNumber,
      featureBranch = featureBranch,
      phaseBranchName = phaseBranchName,
      forgeType = forgeType,
      gitlabHost = gitlabHost,
      repository = repository,
      prUrl = prUrl,
      prNumber = prNumber
    )
    outcome match
      case Left(err) =>
        env.console.err(s"Error: $err")
        CommandResult.error
      case Right(r) => execute(r, env)

  private def readConfigAndForge(
      env: CommandEnv
  ): Either[String, (ProjectConfiguration, ForgeType)] =
    val configPath = env.cwd / Constants.Paths.IwDir / "config.conf"
    if !env.fs.exists(configPath) then
      Left("No config found. Run 'iw init' first.")
    else
      for
        hocon <- env.fs.read(configPath)
        config <- ConfigSerializer.fromHocon(hocon)
      yield (
        config,
        ForgeType.resolve(env.git.getRemoteUrl(env.cwd), config.trackerType)
      )

  private def resolveRepository(
      config: ProjectConfiguration,
      remoteOpt: Option[GitRemote]
  ): Either[String, String] =
    config.repository match
      case Some(r) => Right(r)
      case None    =>
        remoteOpt.flatMap(r => r.extractRepositoryPath.toOption) match
          case Some(r) => Right(r)
          case None    =>
            Left(
              "Cannot determine repository. Set 'tracker.repository' in .iw/config.conf"
            )

  private def validatePrUrl(
      forgeType: ForgeType,
      repository: String,
      prUrl: String
  ): Either[String, Unit] =
    forgeType match
      case ForgeType.GitHub =>
        val expectedPrefix = s"https://github.com/$repository/pull/"
        if prUrl.startsWith(expectedPrefix) then Right(())
        else Left(s"PR URL does not match repository '$repository'.")
      case ForgeType.GitLab =>
        if prUrl.contains(s"/$repository/-/merge_requests/") then Right(())
        else Left(s"MR URL does not match repository '$repository'.")

  private def tryUpdateState(
      env: CommandEnv,
      reviewPath: os.Path,
      input: ReviewStateUpdater.UpdateInput
  ): Unit =
    if env.fs.exists(reviewPath) then
      env.reviewState.update(reviewPath, input) match
        case Left(err) =>
          env.console.err(
            s"Error: Warning: Failed to update review-state: $err"
          )
        case Right(_) => ()

  private def reviewPathFor(r: Resolved, env: CommandEnv): os.Path =
    env.cwd / "project-management" / "issues" / r.issueId.value /
      "review-state.json"

  private def execute(r: Resolved, env: CommandEnv): CommandResult =
    val reviewPath = reviewPathFor(r, env)
    tryUpdateState(
      env,
      reviewPath,
      ReviewStateUpdater.UpdateInput(
        status = Some("ci_pending"),
        displayText = Some(s"Phase ${r.phaseNumber.value}: Waiting for CI"),
        displayType = Some("progress")
      )
    )
    val startTime = env.clock.now
    retryLoop(r, env, startTime, attempt = 0) match
      case CommandResult(0) => doMergeAndAdvance(r, env)
      case other            => other

  private def retryLoop(
      r: Resolved,
      env: CommandEnv,
      startTime: Long,
      attempt: Int
  ): CommandResult =
    poll(r, env, startTime) match
      case Right(verdict) =>
        verdict match
          case CIVerdict.AllPassed | CIVerdict.NoChecksFound =>
            CommandResult.ok
          case CIVerdict.SomeFailed(failed) =>
            handleFailure(r, env, attempt, failed, startTime)
          case CIVerdict.StillRunning | CIVerdict.TimedOut =>
            // poll returns Left on timeout; StillRunning is handled inside poll loop
            CommandResult.error
      case Left(result) => result

  private def handleFailure(
      r: Resolved,
      env: CommandEnv,
      attempt: Int,
      failed: List[CICheckResult],
      startTime: Long
  ): CommandResult =
    env.console.err("Error: CI checks failed:")
    failed.foreach { check =>
      val urlPart = check.url.fold("")(u => s" — $u")
      env.console.err(s"Error:   ${check.name}: ${check.status}$urlPart")
    }
    val recoveryActions = env.hooks.recoveryActions
    val reviewPath = reviewPathFor(r, env)
    if recoveryActions.isEmpty then
      env.console.out(
        "Warning: No recovery action hook installed. Cannot attempt automatic recovery."
      )
      env.console.err(s"Error: PR is at ${r.prUrl}. Fix the failures manually.")
      tryUpdateState(
        env,
        reviewPath,
        ReviewStateUpdater.UpdateInput(activity = Some("waiting"))
      )
      CommandResult.error
    else if PhaseMergeModel.shouldRetry(
        attempt,
        PhaseMergeConfig(
          timeoutMs = r.timeoutMs,
          pollIntervalMs = r.pollIntervalMs,
          maxRetries = r.maxRetries
        )
      )
    then
      invokeRecoveryAction(r, env, attempt, failed, recoveryActions)
      retryLoop(r, env, startTime, attempt + 1)
    else
      env.console.err(
        s"Error: CI checks still failing after ${r.maxRetries} recovery attempt(s). Giving up."
      )
      env.console.err(s"Error: PR is at ${r.prUrl}. Fix the failures manually.")
      tryUpdateState(
        env,
        reviewPath,
        ReviewStateUpdater.UpdateInput(activity = Some("waiting"))
      )
      CommandResult.error

  private def poll(
      r: Resolved,
      env: CommandEnv,
      startTime: Long
  ): Either[CommandResult, CIVerdict] =
    val elapsed = env.clock.now - startTime
    val reviewPath = reviewPathFor(r, env)
    if elapsed > r.timeoutMs then
      tryUpdateState(
        env,
        reviewPath,
        ReviewStateUpdater.UpdateInput(activity = Some("waiting"))
      )
      env.console.err(
        s"Error: Timed out waiting for CI checks after ${PhaseMergeModel.formatDuration(r.timeoutMs)}."
      )
      env.console.err(
        s"Error: PR is at ${r.prUrl}. You can merge manually once CI passes."
      )
      Left(CommandResult.error)
    else
      env.tracker
        .fetchCheckStatuses(
          r.forgeType,
          r.prNumber,
          r.repository,
          r.gitlabHost
        ) match
        case Left(err) =>
          env.console.err(s"Error: Failed to fetch CI check statuses: $err")
          Left(CommandResult.error)
        case Right(checks) =>
          val verdict = PhaseMergeModel.evaluateChecks(checks)
          verdict match
            case CIVerdict.AllPassed     => Right(verdict)
            case CIVerdict.NoChecksFound => Right(verdict)
            case CIVerdict.StillRunning  =>
              env.console.out(
                s"CI still running. Waiting ${PhaseMergeModel.formatDuration(r.pollIntervalMs)}..."
              )
              env.clock.sleep(r.pollIntervalMs)
              poll(r, env, startTime)
            case CIVerdict.SomeFailed(_) => Right(verdict)
            case CIVerdict.TimedOut      => Right(verdict)

  private def invokeRecoveryAction(
      r: Resolved,
      env: CommandEnv,
      attempt: Int,
      failed: List[CICheckResult],
      actions: List[RecoveryAction]
  ): Unit =
    val display = s"${attempt + 1}/${r.maxRetries}"
    env.console.out(s"Invoking recovery action (attempt $display)...")
    val reviewPath = reviewPathFor(r, env)
    tryUpdateState(
      env,
      reviewPath,
      ReviewStateUpdater.UpdateInput(
        status = Some("ci_fixing"),
        displayText =
          Some(s"Phase ${r.phaseNumber.value}: CI Fixing (attempt $display)")
      )
    )
    val ctx = RecoveryContext(
      failedChecks = failed,
      prUrl = r.prUrl,
      branch = r.phaseBranchName,
      attempt = attempt,
      maxRetries = r.maxRetries
    )
    actions.head.recover(ctx)
    tryUpdateState(
      env,
      reviewPath,
      ReviewStateUpdater.UpdateInput(
        status = Some("ci_pending"),
        displayText = Some(s"Phase ${r.phaseNumber.value}: Waiting for CI")
      )
    )

  private def doMergeAndAdvance(r: Resolved, env: CommandEnv): CommandResult =
    env.console.out(s"Merging PR: ${r.prUrl}")
    env.tracker.mergeWithDelete(r.forgeType, r.prUrl, r.gitlabHost) match
      case Left(err) =>
        env.console.err(s"Error: $err")
        CommandResult.error
      case Right(()) =>
        val advance = for
          _ <- env.git.checkoutBranch(r.featureBranch, env.cwd)
          _ <- env.git.fetchAndReset(r.featureBranch, env.cwd)
        yield ()
        advance match
          case Left(err) =>
            env.console.err(s"Error: $err")
            CommandResult.error
          case Right(()) =>
            val reviewPath = reviewPathFor(r, env)
            tryUpdateState(
              env,
              reviewPath,
              ReviewStateUpdater.UpdateInput(
                status = Some("phase_merged"),
                displayText = Some(s"Phase ${r.phaseNumber.value}: Merged"),
                displayType = Some("success"),
                badges = Some(List(("Complete", "success"))),
                badgesMode = ReviewStateUpdater.ArrayMergeMode.Append
              )
            )
            if env.fs.exists(reviewPath) then
              env.git
                .commitFileWithRetry(
                  reviewPath,
                  s"chore(${r.issueId.value}): update review-state for phase ${r.phaseNumber.value}",
                  env.cwd
                )
                .left
                .foreach(err =>
                  env.console.err(
                    s"Error: Warning: Failed to commit review-state update: $err"
                  )
                )
            env.console.out(
              PhaseOutput
                .MergeOutput(
                  issueId = r.issueId.value,
                  phaseNumber = r.phaseNumber.value,
                  prUrl = r.prUrl,
                  featureBranch = r.featureBranch
                )
                .toJson
            )
            CommandResult.ok

object PhaseMergeArgs:
  def parseDurationArg(input: String, flag: String): Either[String, Long] =
    PhaseMergeModel.parseDuration(input).left.map(err => s"Invalid $flag: $err")

  def parseMaxRetries(input: String): Either[String, Int] =
    input.toIntOption match
      case None =>
        Left(s"Invalid --max-retries: '$input' is not a valid integer")
      case Some(n) if n < 0 =>
        Left("Invalid --max-retries: value must not be negative")
      case Some(n) => Right(n)
