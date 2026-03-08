// PURPOSE: Stages all changes, commits with a structured message, and updates phase task file
// PURPOSE: Usage: iw phase-commit --title TITLE [--items ITEM1,ITEM2,...] [--issue-id ID] [--phase-number N]

import iw.core.model.*
import iw.core.adapters.*
import iw.core.output.*

@main def phaseCommit(args: String*): Unit =
  val argList = args.toList

  // Parse --title (required)
  val titleArg = argList
    .sliding(2)
    .collectFirst { case "--title" :: value :: Nil => value }

  // Parse --items (optional, comma-separated)
  val itemsArg = argList
    .sliding(2)
    .collectFirst { case "--items" :: value :: Nil => value }
    .map(_.split(",").toList.map(_.trim).filter(_.nonEmpty))
    .getOrElse(Nil)

  // Parse optional overrides
  val issueIdArg = argList
    .sliding(2)
    .collectFirst { case "--issue-id" :: value :: Nil => value }

  val phaseNumberArg = argList
    .sliding(2)
    .collectFirst { case "--phase-number" :: value :: Nil => value }

  // Require --title
  val title = titleArg match
    case Some(t) => t
    case None =>
      Output.error("Missing required argument: --title")
      Output.error("Usage: iw phase-commit --title TITLE [--items ITEM1,ITEM2,...] [--issue-id ID] [--phase-number N]")
      sys.exit(1)

  // Get current branch
  val currentBranch = GitAdapter.getCurrentBranch(os.pwd) match
    case Left(err) =>
      Output.error(s"Failed to get current branch: $err")
      sys.exit(1)
    case Right(branch) => branch

  // Verify we ARE on a phase sub-branch
  val (featureBranch, phaseNum) = currentBranch match
    case PhaseBranch(fb, pn) => (fb, pn)
    case _ =>
      Output.error(s"Not on a phase sub-branch (current branch: '$currentBranch'). Run 'iw phase-start' first.")
      sys.exit(1)

  // Resolve issue ID (from arg or feature branch)
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
          Output.error(s"Cannot determine issue ID from branch '$featureBranch': $err")
          sys.exit(1)
        case Right(id) => id

  // Resolve phase number (from arg or branch)
  val phaseNumber = phaseNumberArg match
    case Some(raw) =>
      PhaseNumber.parse(raw) match
        case Left(err) =>
          Output.error(err)
          sys.exit(1)
        case Right(pn) => pn
    case None =>
      PhaseNumber.parse(phaseNum) match
        case Left(err) =>
          Output.error(s"Could not parse phase number from branch: $err")
          sys.exit(1)
        case Right(pn) => pn

  // Stage all changes
  GitAdapter.stageAll(os.pwd) match
    case Left(err) =>
      Output.error(s"Failed to stage changes: $err")
      sys.exit(1)
    case Right(_) => ()

  // Build commit message
  val message = CommitMessage.build(title, itemsArg)

  // Commit
  val commitSha = GitAdapter.commit(message, os.pwd) match
    case Left(err) =>
      Output.error(s"Failed to commit: $err")
      sys.exit(1)
    case Right(sha) => sha

  // Count committed files using diff against parent
  val parentSha = s"${commitSha}^"
  val filesCommitted = GitAdapter.diffNameOnly(parentSha, os.pwd) match
    case Left(_) => 0  // best-effort, don't fail
    case Right(files) => files.length

  // Update phase task file (best-effort)
  val taskFilePath = os.pwd / "project-management" / "issues" / issueId.value / s"phase-${phaseNumber.value}-tasks.md"
  if os.exists(taskFilePath) then
    val content = os.read(taskFilePath)
    val afterComplete = PhaseTaskFile.markComplete(content)
    val afterReviewed = PhaseTaskFile.markReviewed(afterComplete)
    os.write.over(taskFilePath, afterReviewed)

  // Output JSON to stdout
  val result = PhaseOutput.CommitOutput(
    issueId = issueId.value,
    phaseNumber = phaseNumber.value,
    commitSha = commitSha,
    filesCommitted = filesCommitted,
    message = message
  )
  println(result.toJson)
