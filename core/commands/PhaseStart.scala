// PURPOSE: Phase-start command logic: parses args, creates phase sub-branch, updates review-state
// PURPOSE: All I/O goes through CommandEnv so the body can be exercised in-VM by harness tests

package iw.core.commands

import iw.core.model.*

object PhaseStart:
  private case class Outcome(
      phaseNumber: PhaseNumber,
      issueId: IssueId,
      branchName: String,
      baselineSha: String
  )

  def run(args: Seq[String], env: CommandEnv): CommandResult =
    val argList = args.toList
    val positional = argList.filterNot(_.startsWith("--"))
    positional.headOption match
      case None =>
        env.console.err("Error: Missing phase-number argument")
        env.console.err(
          "Error: Usage: iw phase-start <phase-number> [--issue-id ID]"
        )
        CommandResult.error
      case Some(phaseNumberRaw) =>
        executeFromArgs(argList, phaseNumberRaw, env)

  private def executeFromArgs(
      argList: List[String],
      phaseNumberRaw: String,
      env: CommandEnv
  ): CommandResult =
    val outcome =
      for
        phaseNumber <- PhaseNumber.parse(phaseNumberRaw)
        featureBranch <- env.git.getCurrentBranch(env.cwd)
        _ <- ensureNotOnPhaseBranch(featureBranch)
        issueId <- PhaseArgs.resolveIssueId(
          PhaseArgs.namedArg(argList, "--issue-id"),
          featureBranch
        )
        branchName = PhaseBranch(featureBranch, phaseNumber).branchName
        _ <- env.git.push(featureBranch, env.cwd, setUpstream = true)
        _ <- env.git.createAndCheckoutBranch(branchName, env.cwd)
        baselineSha <- env.git.getFullHeadSha(env.cwd)
      yield Outcome(phaseNumber, issueId, branchName, baselineSha)

    outcome match
      case Left(err) =>
        env.console.err(s"Error: $err")
        CommandResult.error
      case Right(o) =>
        maybeUpdateReviewState(env, o.issueId, o.phaseNumber)
        env.console.out(
          PhaseOutput
            .StartOutput(
              issueId = o.issueId.value,
              phaseNumber = o.phaseNumber.value,
              branch = o.branchName,
              baselineSha = o.baselineSha
            )
            .toJson
        )
        CommandResult.ok

  private def ensureNotOnPhaseBranch(
      featureBranch: String
  ): Either[String, Unit] =
    featureBranch match
      case PhaseBranch(_, _) =>
        Left(
          s"Already on a phase sub-branch '$featureBranch'. Use 'iw phase-commit' to commit your work."
        )
      case _ => Right(())

  private def maybeUpdateReviewState(
      env: CommandEnv,
      issueId: IssueId,
      phaseNumber: PhaseNumber
  ): Unit =
    val reviewStatePath =
      env.cwd / "project-management" / "issues" / issueId.value / "review-state.json"
    if env.fs.exists(reviewStatePath) then
      env.reviewState.update(
        reviewStatePath,
        ReviewStateUpdater.UpdateInput(
          status = Some("implementing"),
          displayText = Some(s"Phase ${phaseNumber.value}: Implementing"),
          displayType = Some("progress"),
          message = Some(s"Phase ${phaseNumber.value} implementation started"),
          badges = Some(List(("In Progress", "info"))),
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
              s"chore(${issueId.value}): update review-state for phase ${phaseNumber.value}",
              env.cwd
            )
            .left
            .foreach(err =>
              env.console.err(
                s"Error: Warning: Failed to commit review-state update: $err"
              )
            )
