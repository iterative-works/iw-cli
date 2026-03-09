// PURPOSE: Stages all changes, commits with a structured message, and updates phase task file
// PURPOSE: Usage: iw phase-commit --title TITLE [--items ITEM1,ITEM2,...] [--issue-id ID] [--phase-number N]

import iw.core.model.*
import iw.core.adapters.*
import iw.core.output.*

@main def phaseCommit(args: String*): Unit =
  val argList = args.toList

  val title = PhaseArgs.namedArg(argList, "--title") match
    case Some(t) => t
    case None =>
      Output.error("Missing required argument: --title")
      Output.error("Usage: iw phase-commit --title TITLE [--items ITEM1,ITEM2,...] [--issue-id ID] [--phase-number N]")
      sys.exit(1)

  val items = PhaseArgs.namedArg(argList, "--items")
    .map(_.split(",").toList.map(_.trim).filter(_.nonEmpty))
    .getOrElse(Nil)

  val currentBranch = CommandHelpers.exitOnError(
    GitAdapter.getCurrentBranch(os.pwd).left.map(err => s"Failed to get current branch: $err")
  )

  val (featureBranch, phaseNumRaw) = currentBranch match
    case PhaseBranch(fb, pn) => (fb, pn)
    case _ =>
      Output.error(s"Not on a phase sub-branch (current branch: '$currentBranch'). Run 'iw phase-start' first.")
      sys.exit(1)

  val issueId = CommandHelpers.exitOnError(
    PhaseArgs.resolveIssueId(PhaseArgs.namedArg(argList, "--issue-id"), featureBranch)
  )

  val phaseNumber = CommandHelpers.exitOnError(
    PhaseArgs.resolvePhaseNumber(PhaseArgs.namedArg(argList, "--phase-number"), phaseNumRaw)
  )

  CommandHelpers.exitOnError(
    GitAdapter.stageAll(os.pwd).left.map(err => s"Failed to stage changes: $err")
  )

  val message = CommitMessage.build(title, items)

  val commitSha = CommandHelpers.exitOnError(
    GitAdapter.commit(message, os.pwd).left.map(err => s"Failed to commit: $err")
  )

  val filesCommitted = GitAdapter.diffNameOnly(s"${commitSha}^", os.pwd) match
    case Left(_)      => 0
    case Right(files) => files.length

  val taskFilePath = os.pwd / "project-management" / "issues" / issueId.value / s"phase-${phaseNumber.value}-tasks.md"
  if os.exists(taskFilePath) then
    val content = os.read(taskFilePath)
    val afterComplete = PhaseTaskFile.markComplete(content)
    val afterReviewed = PhaseTaskFile.markReviewed(afterComplete)
    os.write.over(taskFilePath, afterReviewed)

  println(PhaseOutput.CommitOutput(
    issueId = issueId.value,
    phaseNumber = phaseNumber.value,
    commitSha = commitSha,
    filesCommitted = filesCommitted,
    message = message
  ).toJson)
