// PURPOSE: Workflow-aware dispatcher for interactive or batch implementation
// PURPOSE: Reads review-state.json to determine workflow type, spawns claude or delegates to batch-implement

import iw.core.model.*
import iw.core.adapters.*
import iw.core.output.*

@main def implement(args: String*): Unit =
  val argList = args.toList
  val cwd = os.pwd

  // --- Argument parsing ---

  // Separate positional args from named args
  val positionalArgs = argList.filterNot(a => a.startsWith("--") || isNamedArgValue(argList, a))
  val issueIdArg = positionalArgs.find(a => a.matches("[A-Z]+-[0-9]+"))

  val batch = PhaseArgs.hasFlag(argList, "--batch")
  val phase = PhaseArgs.namedArg(argList, "--phase")
  val model = PhaseArgs.namedArg(argList, "--model")
  val maxTurns = PhaseArgs.namedArg(argList, "--max-turns")
  val maxBudgetUsd = PhaseArgs.namedArg(argList, "--max-budget-usd")

  // --- Issue ID resolution ---

  val currentBranch = CommandHelpers.exitOnError(GitAdapter.getCurrentBranch(cwd))

  val issueId = issueIdArg match
    case Some(raw) =>
      CommandHelpers.exitOnError(IssueId.parse(raw))
    case None =>
      IssueId.fromBranch(currentBranch) match
        case Right(id) => id
        case Left(_) =>
          Output.error("Cannot determine issue ID. Provide it as a positional argument (e.g. IWLE-123)")
          Output.error("Usage: iw implement [ISSUE_ID] [--batch] [--phase N] [--model MODEL]")
          sys.exit(1)

  // --- Batch mode: delegate to iw batch-implement ---

  if batch then
    val iwScript = (cwd / "iw").toString
    val batchArgs = buildBatchArgs(issueId, model, maxTurns, maxBudgetUsd, argList)
    val exitCode = ProcessAdapter.runInteractive(Seq(iwScript, "batch-implement") ++ batchArgs)
    if exitCode != 0 then sys.exit(exitCode)
  else
    // --- Interactive mode ---

    val issueDir = cwd / "project-management" / "issues" / issueId.value
    val reviewStatePath = issueDir / "review-state.json"

    val reviewStateJson = ReviewStateAdapter.read(reviewStatePath) match
      case Right(json) => json
      case Left(err) =>
        Output.error(s"Cannot read review-state.json: $err")
        Output.error(s"Expected at: $reviewStatePath")
        sys.exit(1)

    val workflowType =
      try
        val json = ujson.read(reviewStateJson)
        json.obj.get("workflow_type").map(_.str)
      catch
        case _: Exception => None

    if workflowType.isEmpty then
      Output.error("Missing workflow_type in review-state.json")
      Output.error("Add a workflow_type field (agile, waterfall, or diagnostic) to review-state.json")
      sys.exit(1)

    val workflowCode = BatchImplement.resolveWorkflowCode(workflowType) match
      case Right(code) => code
      case Left(err) =>
        Output.error(err)
        sys.exit(1)

    if !ProcessAdapter.commandExists("claude") then
      Output.error("claude CLI is not available on PATH.")
      Output.error("Install claude: https://docs.anthropic.com/en/docs/claude-code")
      sys.exit(1)

    val basePrompt = s"/iterative-works:$workflowCode-implement ${issueId.value}"
    val prompt = phase match
      case Some(n) => s"$basePrompt --phase $n"
      case None    => basePrompt

    val claudeCmd = buildClaudeCmd(prompt, model)

    val exitCode = ProcessAdapter.runInteractive(claudeCmd)
    if exitCode != 0 then sys.exit(exitCode)

// Build the claude command sequence
private def buildClaudeCmd(prompt: String, model: Option[String]): Seq[String] =
  val base = Seq("claude", "--dangerously-skip-permissions", "-p", prompt)
  model match
    case Some(m) => base ++ Seq("--model", m)
    case None    => base

// Build args to pass through to batch-implement
// Note: --phase is not forwarded because batch-implement discovers phases from tasks.md
private def buildBatchArgs(
  issueId: IssueId,
  model: Option[String],
  maxTurns: Option[String],
  maxBudgetUsd: Option[String],
  argList: List[String]
): Seq[String] =
  val knownFlags = Set("--batch", "--phase", "--model", "--max-turns", "--max-budget-usd")
  val passthroughFlags = argList.filter(a => a.startsWith("--") && !knownFlags.contains(a))
  Seq(issueId.value) ++
    model.toSeq.flatMap(m => Seq("--model", m)) ++
    maxTurns.toSeq.flatMap(t => Seq("--max-turns", t)) ++
    maxBudgetUsd.toSeq.flatMap(b => Seq("--max-budget-usd", b)) ++
    passthroughFlags

// Helper: check if an arg is a value for a named flag (i.e., its predecessor is a --flag)
private def isNamedArgValue(argList: List[String], arg: String): Boolean =
  argList.sliding(2).exists {
    case flag :: value :: Nil => flag.startsWith("--") && value == arg
    case _ => false
  }
