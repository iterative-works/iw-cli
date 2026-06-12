// PURPOSE: Phase-advance command logic: verifies phase PR merged, advances feature branch
// PURPOSE: All I/O goes through CommandEnv so the body can be exercised in-VM by harness tests

package iw.core.commands

import iw.core.model.*

object PhaseAdvance:
  private case class BranchPlan(
      featureBranch: String,
      phaseBranchName: String,
      phaseNumRaw: String
  )

  private case class Resolved(
      issueId: IssueId,
      phaseNumber: PhaseNumber,
      featureBranch: String,
      phaseBranchName: String,
      forgeType: ForgeType
  )

  def run(args: Seq[String], env: CommandEnv): CommandResult =
    val argList = args.toList
    val outcome = for
      currentBranch <- env.git.getCurrentBranch(env.cwd)
      branchPlan <- resolveBranches(currentBranch, argList)
      issueId <- PhaseArgs.resolveIssueId(
        PhaseArgs.namedArg(argList, "--issue-id"),
        branchPlan.featureBranch
      )
      phaseNumber <- PhaseArgs.resolvePhaseNumber(
        PhaseArgs.namedArg(argList, "--phase-number"),
        branchPlan.phaseNumRaw
      )
      forgeType <- resolveForgeType(env)
    yield (
      currentBranch,
      Resolved(
        issueId = issueId,
        phaseNumber = phaseNumber,
        featureBranch = branchPlan.featureBranch,
        phaseBranchName = branchPlan.phaseBranchName,
        forgeType = forgeType
      )
    )

    outcome match
      case Left(err) =>
        env.console.err(s"Error: $err")
        CommandResult.error
      case Right((currentBranch, resolved)) =>
        advance(currentBranch, resolved, env)

  private def resolveBranches(
      currentBranch: String,
      argList: List[String]
  ): Either[String, BranchPlan] =
    currentBranch match
      case PhaseBranch(fb, pn) =>
        Right(BranchPlan(fb, currentBranch, pn))
      case _ =>
        PhaseArgs.namedArg(argList, "--phase-number") match
          case None =>
            Left(
              s"Cannot determine phase number from branch '$currentBranch'. Provide --phase-number N."
            )
          case Some(raw) =>
            for parsed <- PhaseNumber.parse(raw)
            yield BranchPlan(
              featureBranch = currentBranch,
              phaseBranchName = PhaseBranch(currentBranch, parsed).branchName,
              phaseNumRaw = parsed.value
            )

  private def resolveForgeType(env: CommandEnv): Either[String, ForgeType] =
    val configPath = env.cwd / Constants.Paths.IwDir / "config.conf"
    if !env.fs.exists(configPath) then
      Left("No config found. Run 'iw init' first.")
    else
      for
        hocon <- env.fs.read(configPath)
        config <- ConfigSerializer.fromHocon(hocon)
      yield ForgeType.resolve(env.git.getRemoteUrl(env.cwd), config.trackerType)

  private def advance(
      currentBranch: String,
      r: Resolved,
      env: CommandEnv
  ): CommandResult =
    if !env.process.commandExists(r.forgeType.cliTool) then
      env.console.err(
        s"Error: ${r.forgeType.cliTool} CLI is not installed. Install it from ${r.forgeType.installUrl}"
      )
      CommandResult.error
    else
      checkMerged(r, env) match
        case Left(err) =>
          env.console.err(s"Error: $err")
          CommandResult.error
        case Right(()) => doAdvance(currentBranch, r, env)

  private def checkMerged(
      r: Resolved,
      env: CommandEnv
  ): Either[String, Unit] =
    r.forgeType match
      case ForgeType.GitHub =>
        val mergedResult = env.process.run(
          Seq(
            "gh",
            "pr",
            "list",
            "--head",
            r.phaseBranchName,
            "--state",
            "merged",
            "--json",
            "url"
          )
        )
        if mergedResult.exitCode != 0 then
          Left(s"Failed to check PR status: ${mergedResult.stderr}")
        else
          val mergedOut = mergedResult.stdout.trim
          if mergedOut != "[]" && mergedOut.nonEmpty then Right(())
          else
            val openResult = env.process.run(
              Seq(
                "gh",
                "pr",
                "list",
                "--head",
                r.phaseBranchName,
                "--state",
                "open",
                "--json",
                "url"
              )
            )
            val isOpen =
              openResult.exitCode == 0 && openResult.stdout.trim != "[]" &&
                openResult.stdout.trim.nonEmpty
            if isOpen then
              Left(
                s"PR for branch '${r.phaseBranchName}' is still open. Merge it first."
              )
            else
              Left(
                s"No merged PR found for phase branch '${r.phaseBranchName}'."
              )
      case ForgeType.GitLab =>
        val mrResult = env.process.run(
          Seq(
            "glab",
            "mr",
            "list",
            "--source-branch",
            r.phaseBranchName,
            "--merged"
          )
        )
        if mrResult.exitCode != 0 then
          Left(s"Failed to check MR status: ${mrResult.stderr}")
        else if mrResult.stdout.trim.nonEmpty then Right(())
        else
          Left(s"No merged PR found for phase branch '${r.phaseBranchName}'.")

  private def doAdvance(
      currentBranch: String,
      r: Resolved,
      env: CommandEnv
  ): CommandResult =
    val pipeline = for
      _ <-
        if currentBranch == r.phaseBranchName then
          env.git.checkoutBranch(r.featureBranch, env.cwd)
        else Right(())
      _ <- env.git.fetchAndReset(r.featureBranch, env.cwd)
      headSha <- env.git.getFullHeadSha(env.cwd)
    yield headSha

    pipeline match
      case Left(err) =>
        env.console.err(s"Error: $err")
        CommandResult.error
      case Right(headSha) =>
        maybeUpdateReviewState(r, env)
        PhaseIndexSync.markPhaseComplete(env, r.issueId, r.phaseNumber)
        env.console.out(
          PhaseOutput
            .AdvanceOutput(
              issueId = r.issueId.value,
              phaseNumber = r.phaseNumber.value,
              branch = r.featureBranch,
              previousBranch = r.phaseBranchName,
              headSha = headSha
            )
            .toJson
        )
        CommandResult.ok

  private def maybeUpdateReviewState(r: Resolved, env: CommandEnv): Unit =
    val reviewStatePath =
      env.cwd / "project-management" / "issues" / r.issueId.value /
        "review-state.json"
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
                s"Error: Warning: Failed to commit review-state update: $err"
              )
            )
