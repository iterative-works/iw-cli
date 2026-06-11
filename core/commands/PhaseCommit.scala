// PURPOSE: Phase-commit command logic: validates worktree, updates task file, commits
// PURPOSE: All I/O goes through CommandEnv so the body can be exercised in-VM by harness tests

package iw.core.commands

import iw.core.model.*

object PhaseCommit:
  private def parsePhaseBranch(
      branch: String
  ): Either[String, (String, String)] =
    branch match
      case PhaseBranch(fb, pn) => Right((fb, pn))
      case _                   =>
        Left(
          s"Not on a phase sub-branch (current branch: '$branch'). Run 'iw phase-start' first."
        )

  private case class Inputs(
      title: String,
      items: List[String],
      issueId: IssueId,
      phaseNumber: PhaseNumber,
      taskFilePath: os.Path
  )

  def run(args: Seq[String], env: CommandEnv): CommandResult =
    val argList = args.toList
    PhaseArgs.namedArg(argList, "--title") match
      case None =>
        env.console.err("Error: Missing required argument: --title")
        env.console.err(
          "Error: Usage: iw phase-commit --title TITLE [--items ITEM1,ITEM2,...] [--issue-id ID] [--phase-number N]"
        )
        CommandResult.error
      case Some(title) =>
        executeFromArgs(argList, title, env)

  private def executeFromArgs(
      argList: List[String],
      title: String,
      env: CommandEnv
  ): CommandResult =
    val items = PhaseArgs
      .namedArg(argList, "--items")
      .map(_.split(",").toList.map(_.trim).filter(_.nonEmpty))
      .getOrElse(Nil)

    val prepared = for
      currentBranch <- env.git.getCurrentBranch(env.cwd)
      branchInfo <- parsePhaseBranch(currentBranch)
      (featureBranch, phaseNumRaw) = branchInfo
      issueId <- PhaseArgs.resolveIssueId(
        PhaseArgs.namedArg(argList, "--issue-id"),
        featureBranch
      )
      phaseNumber <- PhaseArgs.resolvePhaseNumber(
        PhaseArgs.namedArg(argList, "--phase-number"),
        phaseNumRaw
      )
    yield Inputs(
      title = title,
      items = items,
      issueId = issueId,
      phaseNumber = phaseNumber,
      taskFilePath = env.cwd / "project-management" / "issues" / issueId.value /
        s"phase-${phaseNumber.value}-tasks.md"
    )

    prepared match
      case Left(err) =>
        env.console.err(s"Error: $err")
        CommandResult.error
      case Right(inputs) => runWithInputs(inputs, env)

  private def runWithInputs(
      inputs: Inputs,
      env: CommandEnv
  ): CommandResult =
    if checkUncheckedTasks(inputs, env) == CommandResult.error then
      CommandResult.error
    else if checkStaging(env) == CommandResult.error then CommandResult.error
    else
      updateTaskFile(inputs, env) match
        case Left(err) =>
          env.console.err(s"Error: $err")
          CommandResult.error
        case Right(()) => doCommit(inputs, env)

  private def checkUncheckedTasks(
      inputs: Inputs,
      env: CommandEnv
  ): CommandResult =
    if !env.fs.exists(inputs.taskFilePath) then CommandResult.ok
    else
      env.fs.read(inputs.taskFilePath) match
        case Left(err) =>
          env.console.err(s"Error: $err")
          CommandResult.error
        case Right(content) =>
          val unchecked = PhaseTaskFile.findUncheckedImplTasks(content)
          if unchecked.isEmpty then CommandResult.ok
          else
            env.console.err("Error: Cannot commit phase with unchecked tasks.")
            env.console.err("Error: ")
            env.console.err(
              "Error: The following tasks are not marked as implemented:"
            )
            unchecked.foreach(line => env.console.err(s"Error:   $line"))
            env.console.err("Error: ")
            env.console.err(
              s"Error: If these tasks have been implemented, check them off in phase-${inputs.phaseNumber.value}-tasks.md and retry."
            )
            env.console.err(
              "Error: If they have NOT been implemented, complete them before committing."
            )
            CommandResult.error

  private def checkStaging(env: CommandEnv): CommandResult =
    env.git.getStagingCheck(env.cwd) match
      case Left(err) =>
        env.console.err(s"Error: $err")
        CommandResult.error
      case Right(check) =>
        check.readyToCommit match
          case Right(()) => CommandResult.ok
          case Left(err) =>
            env.console.err("Error: Worktree is not ready for phase-commit.")
            env.console.err("Error: ")
            err.split("\n").foreach(line => env.console.err(s"Error: $line"))
            env.console.err("Error: ")
            env.console.err(
              "Error: Stage your implementation files with `git add`, then re-run phase-commit."
            )
            CommandResult.error

  private def updateTaskFile(
      inputs: Inputs,
      env: CommandEnv
  ): Either[String, Unit] =
    if !env.fs.exists(inputs.taskFilePath) then Right(())
    else
      for
        content <- env.fs.read(inputs.taskFilePath)
        updated = PhaseTaskFile.markReviewed(
          PhaseTaskFile.markComplete(content)
        )
        _ <- env.fs.write(inputs.taskFilePath, updated)
        _ <- env.git.stageFiles(Seq(inputs.taskFilePath), env.cwd)
      yield ()

  private def doCommit(inputs: Inputs, env: CommandEnv): CommandResult =
    val message = CommitMessage.build(inputs.title, inputs.items)
    env.git.commit(message, env.cwd) match
      case Left(err) =>
        env.console.err(s"Error: $err")
        CommandResult.error
      case Right(commitSha) =>
        val filesCommitted = env.git.diffNameOnly(s"$commitSha^", env.cwd) match
          case Left(_)      => 0
          case Right(files) => files.length
        env.console.out(
          PhaseOutput
            .CommitOutput(
              issueId = inputs.issueId.value,
              phaseNumber = inputs.phaseNumber.value,
              commitSha = commitSha,
              filesCommitted = filesCommitted,
              message = message
            )
            .toJson
        )
        CommandResult.ok
