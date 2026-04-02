// PURPOSE: Orchestrates unattended phase-by-phase implementation via the claude CLI
// PURPOSE: Invokes claude per phase, handles merge/recovery outcomes, and advances tasks.md

import iw.core.model.*
import iw.core.adapters.*
import iw.core.output.*

import java.io.{FileWriter, BufferedWriter}
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@main def batchImplement(args: String*): Unit =
  val argList = args.toList
  val cwd = os.pwd

  // --- Argument parsing ---

  // Separate positional args (not starting with --) from named args
  val positionalArgs =
    argList.filterNot(a => a.startsWith("--") || isNamedArgValue(argList, a))
  val issueIdArg = positionalArgs.find(a => a.matches("[A-Z]+-[0-9]+"))
  val workflowCodeArg =
    positionalArgs.find(a => a == "ag" || a == "wf" || a == "dx")

  val model = PhaseArgs.namedArg(argList, "--model").getOrElse("opus")
  val maxTurns =
    PhaseArgs.namedArg(argList, "--max-turns").flatMap(_.toIntOption)
  val maxRetries = PhaseArgs
    .namedArg(argList, "--max-retries")
    .flatMap(_.toIntOption)
    .getOrElse(1)
  val maxBudgetUsd =
    PhaseArgs.namedArg(argList, "--max-budget-usd").flatMap(_.toDoubleOption)

  // --- Issue ID resolution ---

  val currentBranch =
    CommandHelpers.exitOnError(GitAdapter.getCurrentBranch(cwd))

  val issueId = issueIdArg match
    case Some(raw) =>
      CommandHelpers.exitOnError(IssueId.parse(raw))
    case None =>
      IssueId.fromBranch(currentBranch) match
        case Right(id) => id
        case Left(_)   =>
          Output.error(
            "Cannot determine issue ID. Provide it as a positional argument (e.g. IW-275)"
          )
          Output.error(
            "Usage: iw batch-implement [ISSUE_ID] [ag|wf] [--model MODEL] [--max-turns N] [--max-retries N] [--max-budget-usd N]"
          )
          sys.exit(1)

  // --- Paths ---

  val issueDir = cwd / "project-management" / "issues" / issueId.value
  val tasksPath = issueDir / "tasks.md"
  val reviewStatePath = issueDir / "review-state.json"

  // --- Pre-flight: tasks.md ---

  if !os.exists(tasksPath) then
    Output.error(s"tasks.md not found at $tasksPath")
    Output.error(
      "Create a tasks.md file with a Phase Index section before running batch-implement."
    )
    sys.exit(1)

  // --- Pre-flight: claude CLI ---

  if !ProcessAdapter.commandExists("claude") then
    Output.error("claude CLI is not available on PATH.")
    Output.error(
      "Install claude: https://docs.anthropic.com/en/docs/claude-code"
    )
    sys.exit(1)

  // --- Config & forge detection ---

  val configPath = cwd / Constants.Paths.IwDir / "config.conf"
  val configOpt = ConfigFileRepository.read(configPath)
  val trackerType =
    configOpt.map(_.trackerType).getOrElse(IssueTrackerType.GitHub)
  val remoteOpt = GitAdapter.getRemoteUrl(cwd)
  val forgeType = ForgeType.resolve(remoteOpt, trackerType)

  // --- Pre-flight: forge CLI ---

  val forgeCli = forgeType.cliTool
  if !ProcessAdapter.commandExists(forgeCli) then
    Output.error(s"$forgeCli CLI is not available on PATH.")
    Output.error(s"Install it from ${forgeType.installUrl}")
    sys.exit(1)

  // --- Pre-flight: clean working tree ---

  val isDirty =
    CommandHelpers.exitOnError(GitAdapter.hasUncommittedChanges(cwd))
  if isDirty then
    Output.error(
      "Working tree has uncommitted changes. Please commit or stash them before running batch-implement."
    )
    sys.exit(1)

  // --- Workflow code resolution ---

  val workflowCode = workflowCodeArg match
    case Some(code) => code
    case None       =>
      // Auto-detect from review-state.json
      val reviewStateJson = ReviewStateAdapter.read(reviewStatePath) match
        case Right(json) => json
        case Left(err)   =>
          Output.error(
            s"Cannot read review-state.json to detect workflow type: $err"
          )
          Output.error(
            "Provide the workflow code as a positional argument: iw batch-implement ISSUE_ID [ag|wf|dx]"
          )
          sys.exit(1)
      val workflowType =
        try
          val json = ujson.read(reviewStateJson)
          json.obj.get("workflow_type").map(_.str)
        catch case _: Exception => None
      CommandHelpers.exitOnError(
        BatchImplement.resolveWorkflowCode(workflowType)
      )

  // --- Logging setup ---

  val claudeTimeoutMs = 60 * 60 * 1000 // 60-minute per-phase limit

  val logPath = issueDir / "batch-implement.log"
  val logWriter = new BufferedWriter(new FileWriter(logPath.toIO, true))

  def log(msg: String): Unit =
    System.err.println(msg)
    logWriter.write(msg)
    logWriter.newLine()
    logWriter.flush()

  val timestamp =
    LocalDateTime.now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
  log(s"=== batch-implement session started at $timestamp ===")
  log(
    s"Issue: ${issueId.value}  Workflow: $workflowCode  Model: $model  MaxTurns: ${maxTurns.getOrElse("unlimited")}  MaxRetries: $maxRetries"
  )
  maxBudgetUsd.foreach(b => log(s"Max budget: $$${b}"))

  // --- Helper: commit any uncommitted changes claude left behind ---

  def commitLeftovers(phase: Int): Unit =
    GitAdapter.hasUncommittedChanges(cwd) match
      case Right(true) =>
        log(s"[phase $phase] Committing uncommitted changes left by claude...")
        CommandHelpers.exitOnError(GitAdapter.stageAll(cwd))
        GitAdapter.commit(
          s"chore: uncommitted changes after phase $phase",
          cwd
        ) match
          case Right(sha) => log(s"[phase $phase] Committed leftovers as $sha")
          case Left(err)  =>
            log(s"[phase $phase] Warning: failed to commit leftovers: $err")
      case _ => ()

  // --- Helper: read status from review-state.json ---

  def readStatus(): String =
    ReviewStateAdapter.read(reviewStatePath) match
      case Left(err) =>
        log(s"Warning: cannot read review-state.json: $err")
        "unknown"
      case Right(json) =>
        try ujson.read(json).obj.get("status").map(_.str).getOrElse("unknown")
        catch case _: Exception => "unknown"

  // --- Helper: recovery prompt for a status ---

  def recoveryPromptFor(status: String): String =
    status match
      case "implementing" =>
        "Continue the implementation workflow. Complete all remaining implementation tasks for this phase, then run the phase-pr command to create the PR."
      case "context_ready" =>
        "The context is ready. Proceed with implementing all tasks for this phase, then create a PR with iw phase-pr."
      case "tasks_ready" =>
        "Tasks are ready. Begin implementing all tasks for this phase, then create a PR with iw phase-pr."
      case "refactoring_complete" =>
        "Refactoring is complete. Verify all tests pass, then create a PR with iw phase-pr."
      case "review_failed" =>
        "The review failed. Address the feedback, fix all issues, and re-create the PR with iw phase-pr."
      case _ =>
        "Check current state and complete remaining steps in the implementation workflow."

  // --- Helper: build claude command ---

  val projectContext = configOpt match
    case Some(config) =>
      val input = ProjectContext.fromConfig(config, remoteOpt)
      ProjectContext.render(input)
    case None => ""

  def claudeCmd(prompt: String, extraFlags: List[String] = Nil): Seq[String] =
    val base = List(
      "claude",
      "--dangerously-skip-permissions",
      "-p",
      prompt,
      "--model",
      model
    ) ++ (if projectContext.nonEmpty then
            List("--append-system-prompt", projectContext)
          else Nil)
    val withTurns =
      maxTurns.map(t => base ++ List("--max-turns", t.toString)).getOrElse(base)
    val withBudget = maxBudgetUsd
      .map(b => withTurns ++ List("--max-cost-usd", b.toString))
      .getOrElse(withTurns)
    withBudget ++ extraFlags

  // --- Helper: mark phase done in tasks.md and commit ---

  def markAndCommitPhase(phaseNum: Int): Unit =
    val tasksContent = os.read(tasksPath)
    BatchImplement.markPhaseComplete(tasksContent, phaseNum) match
      case Left(err) if err.contains("already marked complete") =>
        log(
          s"[phase $phaseNum] Phase already marked complete in tasks.md, skipping."
        )
      case Left(err) =>
        log(
          s"[phase $phaseNum] Fatal: could not mark phase complete in tasks.md: $err"
        )
        log(
          s"[phase $phaseNum] Fix tasks.md manually and re-run batch-implement."
        )
        logWriter.close()
        sys.exit(1)
      case Right(updated) =>
        os.write.over(tasksPath, updated)
        CommandHelpers.exitOnError(GitAdapter.stageAll(cwd))
        GitAdapter.commit(
          s"chore: mark Phase $phaseNum complete in tasks.md",
          cwd
        ) match
          case Right(sha) =>
            log(s"[phase $phaseNum] tasks.md updated and committed ($sha)")
          case Left(err) =>
            log(
              s"[phase $phaseNum] Warning: failed to commit tasks.md update: $err"
            )

  // --- Helper: invoke phase-merge subprocess ---

  def invokePhaseMerge(phaseNum: Int): Unit =
    log(s"[phase $phaseNum] Invoking phase-merge to wait for CI and merge...")
    val phaseMergeCmd = Seq((cwd / "iw").toString, "phase-merge")
    // phase-merge has its own internal timeout (default 30m) plus may run recovery agents.
    // Use a very large outer timeout to avoid killing it prematurely.
    val phaseMergeTimeoutMs = 4 * 60 * 60 * 1000 // 4 hours
    val exitCode =
      ProcessAdapter.runStreaming(phaseMergeCmd, phaseMergeTimeoutMs)
    if exitCode != 0 then
      log(
        s"[phase $phaseNum] phase-merge failed (exit code $exitCode). Stopping batch."
      )
      log(
        s"[phase $phaseNum] Check the PR status and resolve manually, then re-run batch-implement."
      )
      logWriter.close()
      sys.exit(1)
    log(s"[phase $phaseNum] phase-merge completed successfully")
    markAndCommitPhase(phaseNum)

  // --- Recovery loop (tail-recursive) ---

  def attemptRecovery(phaseNum: Int, attemptsLeft: Int, status: String): Unit =
    if attemptsLeft <= 0 then
      log(
        s"[phase $phaseNum] Exhausted $maxRetries recovery attempts; still in status '$status'"
      )
      log(
        s"[phase $phaseNum] Resolve the issue manually and re-run batch-implement."
      )
      logWriter.close()
      sys.exit(1)
    else
      log(
        s"[phase $phaseNum] Recovery attempt ${maxRetries - attemptsLeft + 1}/$maxRetries (status: $status)"
      )
      val recoverExitCode = ProcessAdapter.runStreaming(
        claudeCmd(recoveryPromptFor(status), List("--continue")),
        claudeTimeoutMs,
        closeStdin = true
      )
      if recoverExitCode != 0 then
        log(
          s"[phase $phaseNum] claude recovery exited with code $recoverExitCode"
        )
      commitLeftovers(phaseNum)
      val newStatus = readStatus()
      log(s"[phase $phaseNum] Status after recovery: $newStatus")
      handleOutcome(phaseNum, newStatus, attemptsLeft - 1)

  // --- Outcome handler ---

  def handleOutcome(
      phaseNum: Int,
      status: String,
      recoveryAttemptsLeft: Int
  ): Unit =
    BatchImplement.decideOutcome(status) match
      case PhaseOutcome.MergePR =>
        invokePhaseMerge(phaseNum)
      case PhaseOutcome.MarkDone =>
        log(s"[phase $phaseNum] Phase already merged; marking done.")
        markAndCommitPhase(phaseNum)
      case PhaseOutcome.Fail(reason) =>
        log(s"[phase $phaseNum] Fatal: $reason")
        logWriter.close()
        sys.exit(1)
      case PhaseOutcome.Recover =>
        attemptRecovery(phaseNum, recoveryAttemptsLeft, status)

  // --- Phase loop (tail-recursive) ---

  @scala.annotation.tailrec
  def runPhases(): Unit =
    val tasksContent = os.read(tasksPath)
    val phases =
      MarkdownTaskParser.parsePhaseIndex(tasksContent.split("\n", -1).toSeq)
    BatchImplement.nextPhase(phases) match
      case None =>
        log("All phases complete.")
      case Some(phaseNum) =>
        log(s"[phase $phaseNum] Starting implementation via claude...")
        val prompt =
          s"/iterative-works:$workflowCode-implement ${issueId.value} --batch --phase $phaseNum"
        log(s"[phase $phaseNum] Invoking: claude -p \"$prompt\"")
        val claudeExitCode = ProcessAdapter.runStreaming(
          claudeCmd(prompt),
          claudeTimeoutMs,
          closeStdin = true
        )
        if claudeExitCode != 0 then
          log(
            s"[phase $phaseNum] claude exited with code $claudeExitCode (entering recovery)"
          )

        commitLeftovers(phaseNum)

        val status = readStatus()
        log(s"[phase $phaseNum] review-state status: $status")

        handleOutcome(phaseNum, status, maxRetries)
        runPhases()

  runPhases()

  // --- Completion flow ---

  log("All phases done. Running completion flow (final PR / release notes)...")
  val completionPrompt =
    s"/iterative-works:$workflowCode-implement ${issueId.value}"
  log(s"Invoking: claude -p \"$completionPrompt\"")
  val completionExitCode = ProcessAdapter.runStreaming(
    claudeCmd(completionPrompt),
    claudeTimeoutMs,
    closeStdin = true
  )
  log(s"Completion flow finished with exit code $completionExitCode")
  logWriter.close()

// Helper: check if an arg is a value for a named flag (i.e., its predecessor is a --flag)
private def isNamedArgValue(argList: List[String], arg: String): Boolean =
  argList.sliding(2).exists {
    case flag :: value :: Nil => flag.startsWith("--") && value == arg
    case _                    => false
  }
