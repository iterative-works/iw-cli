// PURPOSE: Creates a phase sub-branch from a feature branch and records baseline SHA
// PURPOSE: Usage: iw phase-start <phase-number> [--issue-id ID]

import iw.core.model.*
import iw.core.adapters.*
import iw.core.output.*

@main def phaseStart(args: String*): Unit =
  // Parse arguments
  val argList = args.toList
  val positional = argList.filterNot(_.startsWith("--"))
  val phaseNumberArg = positional.headOption
  val issueIdArg = argList
    .sliding(2)
    .collectFirst { case "--issue-id" :: value :: Nil => value }

  // Require phase number
  val phaseNumberRaw = phaseNumberArg match
    case Some(n) => n
    case None =>
      Output.error("Missing phase-number argument")
      Output.error("Usage: iw phase-start <phase-number> [--issue-id ID]")
      sys.exit(1)

  // Parse and validate phase number
  val phaseNumber = PhaseNumber.parse(phaseNumberRaw) match
    case Left(err) =>
      Output.error(err)
      sys.exit(1)
    case Right(pn) => pn

  // Get current branch (this is the feature branch)
  val featureBranch = GitAdapter.getCurrentBranch(os.pwd) match
    case Left(err) =>
      Output.error(s"Failed to get current branch: $err")
      sys.exit(1)
    case Right(branch) => branch

  // Verify we're NOT already on a phase sub-branch
  featureBranch match
    case PhaseBranch(_, _) =>
      Output.error(s"Already on a phase sub-branch '$featureBranch'. Use 'iw phase-commit' to commit your work.")
      sys.exit(1)
    case _ => ()

  // Resolve issue ID
  val issueId = issueIdArg match
    case Some(rawId) =>
      IssueId.parse(rawId) match
        case Left(err) =>
          Output.error(err)
          sys.exit(1)
        case Right(id) => id
    case None =>
      IssueId.fromBranch(featureBranch) match
        case Left(err) =>
          Output.error(s"Cannot determine issue ID: $err")
          sys.exit(1)
        case Right(id) => id

  // Build phase branch name
  val branchName = PhaseBranch(featureBranch, phaseNumber).branchName

  // Create and checkout the phase sub-branch
  GitAdapter.createAndCheckoutBranch(branchName, os.pwd) match
    case Left(err) =>
      Output.error(err)
      sys.exit(1)
    case Right(_) => ()

  // Capture baseline SHA
  val baselineSha = GitAdapter.getFullHeadSha(os.pwd) match
    case Left(err) =>
      Output.error(s"Failed to get baseline SHA: $err")
      sys.exit(1)
    case Right(sha) => sha

  // Update review-state (best-effort — skip if file doesn't exist)
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
      case Right(_) => ()

  // Output JSON to stdout
  val output = PhaseOutput.StartOutput(
    issueId = issueId.value,
    phaseNumber = phaseNumber.value,
    branch = branchName,
    baselineSha = baselineSha
  )
  println(output.toJson)
