// PURPOSE: Creates a phase sub-branch from a feature branch and records baseline SHA
// USAGE: iw phase-start <phase-number> [--issue-id ID]

import iw.core.model.*
import iw.core.adapters.*
import iw.core.output.*

@main def phaseStart(args: String*): Unit =
  val argList = args.toList
  val positional = argList.filterNot(_.startsWith("--"))
  val phaseNumberRaw = positional.headOption match
    case Some(n) => n
    case None =>
      Output.error("Missing phase-number argument")
      Output.error("Usage: iw phase-start <phase-number> [--issue-id ID]")
      sys.exit(1)

  val phaseNumber = CommandHelpers.exitOnError(PhaseNumber.parse(phaseNumberRaw))

  val featureBranch = CommandHelpers.exitOnError(GitAdapter.getCurrentBranch(os.pwd))

  featureBranch match
    case PhaseBranch(_, _) =>
      Output.error(s"Already on a phase sub-branch '$featureBranch'. Use 'iw phase-commit' to commit your work.")
      sys.exit(1)
    case _ => ()

  val issueId = CommandHelpers.exitOnError(
    PhaseArgs.resolveIssueId(PhaseArgs.namedArg(argList, "--issue-id"), featureBranch)
  )

  val branchName = PhaseBranch(featureBranch, phaseNumber).branchName

  CommandHelpers.exitOnError(GitAdapter.createAndCheckoutBranch(branchName, os.pwd))

  val baselineSha = CommandHelpers.exitOnError(GitAdapter.getFullHeadSha(os.pwd))

  val reviewStatePath = os.pwd / "project-management" / "issues" / issueId.value / "review-state.json"
  if os.exists(reviewStatePath) then
    ReviewStateAdapter.update(reviewStatePath, ReviewStateUpdater.UpdateInput(
      status = Some("implementing"),
      displayText = Some(s"Phase ${phaseNumber.value}: Implementing"),
      displayType = Some("progress"),
      message = Some(s"Phase ${phaseNumber.value} implementation started"),
      badges = Some(List(("In Progress", "info"))),
      badgesMode = ReviewStateUpdater.ArrayMergeMode.Append
    )) match
      case Left(err) => Output.error(s"Warning: Failed to update review-state: $err")
      case Right(_) =>
        // Commit the review-state update so the feature branch stays clean
        GitAdapter.stageFiles(Seq(reviewStatePath), os.pwd)
          .flatMap(_ => GitAdapter.commit(s"chore(${issueId.value}): update review-state for phase ${phaseNumber.value}", os.pwd))
          .left.foreach(err => Output.error(s"Warning: Failed to commit review-state update: $err"))

  println(PhaseOutput.StartOutput(
    issueId = issueId.value,
    phaseNumber = phaseNumber.value,
    branch = branchName,
    baselineSha = baselineSha
  ).toJson)
