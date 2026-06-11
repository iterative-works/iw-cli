// PURPOSE: Phase-pr command logic: push phase branch, create PR/MR, optionally batch-merge
// PURPOSE: All I/O goes through CommandEnv so the body can be exercised in-VM by harness tests

package iw.core.commands

import iw.core.model.*

object PhasePr:
  private case class Resolved(
      title: String,
      bodyArg: Option[String],
      batchMode: Boolean,
      issueId: IssueId,
      phaseNumber: PhaseNumber,
      featureBranch: String,
      phaseBranchName: String,
      forgeType: ForgeType,
      remoteOpt: Option[GitRemote],
      gitlabHost: Option[String],
      repository: String
  )

  def run(args: Seq[String], env: CommandEnv): CommandResult =
    val argList = args.toList
    PhaseArgs.namedArg(argList, "--title") match
      case None =>
        env.console.err("Error: Missing required argument: --title")
        env.console.err(
          "Error: Usage: iw phase-pr --title TITLE [--body BODY] [--batch] [--issue-id ID] [--phase-number N]"
        )
        CommandResult.error
      case Some(title) => resolve(argList, title, env)

  private def resolve(
      argList: List[String],
      title: String,
      env: CommandEnv
  ): CommandResult =
    val bodyArg = PhaseArgs.namedArg(argList, "--body")
    val batchMode = PhaseArgs.hasFlag(argList, "--batch")

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
    yield Resolved(
      title = title,
      bodyArg = bodyArg,
      batchMode = batchMode,
      issueId = issueId,
      phaseNumber = phaseNumber,
      featureBranch = featureBranch,
      phaseBranchName = phaseBranchName,
      forgeType = forgeType,
      remoteOpt = remoteOpt,
      gitlabHost = gitlabHost,
      repository = repository
    )

    outcome match
      case Left(err) =>
        env.console.err(s"Error: $err")
        CommandResult.error
      case Right(r) => createPrAndMaybeMerge(r, env)

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

  private def createPrAndMaybeMerge(
      r: Resolved,
      env: CommandEnv
  ): CommandResult =
    env.git.push(r.phaseBranchName, env.cwd, setUpstream = true) match
      case Left(err) =>
        env.console.err(s"Error: $err")
        CommandResult.error
      case Right(()) =>
        val body = r.bodyArg.getOrElse(defaultBody(r))
        env.tracker.createPullRequest(
          r.forgeType,
          r.repository,
          r.phaseBranchName,
          r.featureBranch,
          r.title,
          body,
          r.gitlabHost
        ) match
          case Left(err) =>
            val verb =
              if r.forgeType == ForgeType.GitHub then "pull" else "merge"
            env.console.err(
              s"Error: The branch '${r.phaseBranchName}' was already pushed. You can create the PR manually."
            )
            env.console.err(s"Error: Failed to create $verb request: $err")
            CommandResult.error
          case Right(prUrl) =>
            if r.batchMode then doBatchMerge(r, prUrl, env)
            else
              updateReviewStateForReview(r, prUrl, env)
              printOutput(r, prUrl, merged = false, env)
              CommandResult.ok

  private def defaultBody(r: Resolved): String =
    val fileUrlBase = r.remoteOpt
      .flatMap(remote =>
        FileUrlBuilder.build(remote, r.phaseBranchName).toOption
      )
    val fileLinks = fileUrlBase
      .map(base => s"\n\n### Files\nBrowse changed files: ${base}")
      .getOrElse("")
    s"Phase ${r.phaseNumber.value} implementation for ${r.issueId.value}.$fileLinks"

  private def doBatchMerge(
      r: Resolved,
      prUrl: String,
      env: CommandEnv
  ): CommandResult =
    env.tracker.mergeSquashAndDelete(r.forgeType, prUrl, r.gitlabHost) match
      case Left(err) =>
        env.console.err(s"Error: $err")
        env.console.err(
          s"Error: PR was created at $prUrl. Run 'iw phase-advance' after merging manually."
        )
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
            updateReviewStateForMerged(r, env)
            printOutput(r, prUrl, merged = true, env)
            CommandResult.ok

  private def updateReviewStateForReview(
      r: Resolved,
      prUrl: String,
      env: CommandEnv
  ): Unit =
    val reviewStatePath = reviewStatePathFor(r, env)
    if env.fs.exists(reviewStatePath) then
      env.reviewState.update(
        reviewStatePath,
        ReviewStateUpdater.UpdateInput(
          status = Some("awaiting_review"),
          displayText = Some(s"Phase ${r.phaseNumber.value}: Awaiting Review"),
          displayType = Some("warning"),
          needsAttention = Some(true),
          prUrl = Some(prUrl),
          badges = Some(List(("Review Needed", "warning"))),
          badgesMode = ReviewStateUpdater.ArrayMergeMode.Append,
          actions =
            Some(List(("view-pr", "View Pull Request", "external-link"))),
          actionsMode = ReviewStateUpdater.ArrayMergeMode.Append
        )
      ) match
        case Left(err) =>
          env.console.err(
            s"Error: Warning: Failed to update review-state: $err"
          )
        case Right(_) =>
          val pipeline = for
            _ <- env.git.commitFileWithRetry(
              reviewStatePath,
              s"chore(${r.issueId.value}): update review-state for phase ${r.phaseNumber.value}",
              env.cwd
            )
            _ <- env.git.push(r.phaseBranchName, env.cwd, setUpstream = false)
          yield ()
          pipeline.left.foreach(err =>
            env.console.err(
              s"Error: Warning: Failed to persist review-state: $err"
            )
          )

  private def updateReviewStateForMerged(r: Resolved, env: CommandEnv): Unit =
    val reviewStatePath = reviewStatePathFor(r, env)
    if env.fs.exists(reviewStatePath) then
      env.reviewState.update(
        reviewStatePath,
        ReviewStateUpdater.UpdateInput(
          status = Some("phase_merged"),
          displayText = Some(s"Phase ${r.phaseNumber.value}: Merged"),
          displayType = Some("success"),
          badges = Some(List(("Complete", "success"))),
          badgesMode = ReviewStateUpdater.ArrayMergeMode.Append
        )
      ) match
        case Left(err) =>
          env.console.err(
            s"Error: Warning: Failed to update review-state: $err"
          )
        case Right(_) =>
          env.git
            .commitFileWithRetry(
              reviewStatePath,
              s"chore(${r.issueId.value}): update review-state for phase ${r.phaseNumber.value}",
              env.cwd
            )
            .left
            .foreach(err =>
              env.console.err(
                s"Error: Warning: Failed to persist review-state: $err"
              )
            )

  private def reviewStatePathFor(r: Resolved, env: CommandEnv): os.Path =
    env.cwd / "project-management" / "issues" / r.issueId.value /
      "review-state.json"

  private def printOutput(
      r: Resolved,
      prUrl: String,
      merged: Boolean,
      env: CommandEnv
  ): Unit =
    env.console.out(
      PhaseOutput
        .PrOutput(
          issueId = r.issueId.value,
          phaseNumber = r.phaseNumber.value,
          prUrl = prUrl,
          headBranch = r.phaseBranchName,
          baseBranch = r.featureBranch,
          merged = merged
        )
        .toJson
    )
