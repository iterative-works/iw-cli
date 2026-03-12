// PURPOSE: Stages all changes, commits with a structured message, and updates phase task file
// USAGE: iw phase-commit --title TITLE [--items ITEM1,ITEM2,...] [--issue-id ID] [--phase-number N]

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

  val currentBranch = CommandHelpers.exitOnError(GitAdapter.getCurrentBranch(os.pwd))

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

  val taskFilePath = os.pwd / "project-management" / "issues" / issueId.value / s"phase-${phaseNumber.value}-tasks.md"
  if os.exists(taskFilePath) then
    val taskContent = os.read(taskFilePath)
    val unchecked = PhaseTaskFile.findUncheckedImplTasks(taskContent)
    if unchecked.nonEmpty then
      Output.error("Cannot commit phase with unchecked tasks.")
      Output.error("")
      Output.error("The following tasks are not marked as implemented:")
      unchecked.foreach(line => Output.error(s"  $line"))
      Output.error("")
      Output.error(s"If these tasks have been implemented, check them off in phase-${phaseNumber.value}-tasks.md and retry.")
      Output.error("If they have NOT been implemented, complete them before committing.")
      sys.exit(1)

  CommandHelpers.exitOnError(GitAdapter.stageAll(os.pwd))

  val message = CommitMessage.build(title, items)

  val commitSha = CommandHelpers.exitOnError(GitAdapter.commit(message, os.pwd))

  val filesCommitted = GitAdapter.diffNameOnly(s"${commitSha}^", os.pwd) match
    case Left(_)      => 0
    case Right(files) => files.length

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
