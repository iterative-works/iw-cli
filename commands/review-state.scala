// PURPOSE: Main dispatcher for review-state subcommands
// PURPOSE: Routes to validate, write, or update operations
// USAGE: iw review-state <subcommand> [args...]
// SUBCOMMANDS:
//   validate <path>         Validate review-state.json file
//   write [options]         Create new review-state.json from scratch
//   update [options]        Update existing review-state.json
// EXAMPLE: iw review-state validate project-management/issues/IW-42/review-state.json
// EXAMPLE: iw review-state write --display-text "Planning" --display-type info
// EXAMPLE: iw review-state update --display-text "Implementing"

import iw.core.model.*
import iw.core.adapters.*
import iw.core.output.*

@main def `review-state`(args: String*): Unit =
  if args.isEmpty then
    Output.error("No subcommand provided")
    showHelp()
    sys.exit(1)

  val subcommand = args.head
  val subcommandArgs = args.tail

  subcommand match
    case "--help" | "-h" =>
      showHelp()
      sys.exit(0)
    case "validate" =>
      validate(subcommandArgs)
    case "write" =>
      write(subcommandArgs)
    case "update" =>
      update(subcommandArgs)
    case _ =>
      Output.error(s"Unknown subcommand: $subcommand")
      Output.info("Available subcommands: validate, write, update")
      sys.exit(1)

private def showHelp(): Unit =
  Output.info("Usage: iw review-state <subcommand> [args...]")
  Output.info("Available subcommands:")
  Output.info("  validate <path>    Validate review-state.json file")
  Output.info("  write [options]    Create new review-state.json from scratch")
  Output.info("  update [options]   Update existing review-state.json")

// ----- validate subcommand -----

private def validate(args: Seq[String]): Unit =
  val argList = args.toList

  if argList.contains("--help") || argList.contains("-h") then
    showValidateHelp()
    sys.exit(0)

  val useStdin = args.contains("--stdin")
  val filePaths = args.filterNot(_.startsWith("--"))

  if !useStdin && filePaths.isEmpty then
    Output.error(
      "No file path provided. Usage: iw review-state validate <file-path> or --stdin"
    )
    sys.exit(1)

  val json =
    if useStdin then scala.io.Source.stdin.mkString
    else
      val path = os.Path(filePaths.head, os.pwd)
      if !os.exists(path) then
        Output.error(s"File not found: $path")
        sys.exit(1)
      os.read(path)

  val result = ReviewStateValidator.validate(json)

  if result.isValid then
    Output.success("Review state is valid")
    result.warnings.foreach { warning =>
      Output.warning(warning)
    }
    sys.exit(0)
  else
    Output.error("Review state validation failed")
    result.errors.foreach { err =>
      Output.info(s"  ${err.field}: ${err.message}")
    }
    result.warnings.foreach { warning =>
      Output.warning(warning)
    }
    sys.exit(1)

private def showValidateHelp(): Unit =
  println("Validate a review-state.json file against the formal schema")
  println()
  println("Usage:")
  println("  iw review-state validate <file-path>")
  println("  iw review-state validate --stdin")
  println()
  println("Arguments:")
  println("  file-path  Path to the review-state.json file to validate")
  println("  --stdin    Read JSON from standard input instead of a file")
  println()
  println("Examples:")
  println(
    "  iw review-state validate project-management/issues/IW-42/review-state.json"
  )
  println("  cat review-state.json | iw review-state validate --stdin")

// ----- write subcommand -----

private def write(args: Seq[String]): Unit =
  val argList = args.toList

  if argList.contains("--help") || argList.contains("-h") then
    showWriteHelp()
    sys.exit(0)

  if argList.contains("--from-stdin") then handleWriteStdin(argList)
  else handleWriteFlags(argList)

private def showWriteHelp(): Unit =
  println("Write a validated review-state.json file from CLI flags or stdin")
  println()
  println("Usage:")
  println("  iw review-state write [options]")
  println("  iw review-state write --from-stdin --output <path>")
  println()
  println("Options:")
  println(
    "  --status <value>                          Optional machine-readable status identifier"
  )
  println(
    "  --display-text <value>                    Primary display text for status badge"
  )
  println(
    "  --display-subtext <value>                 Optional secondary display text"
  )
  println(
    "  --display-type <value>                    Display type: info, success, warning, error, progress"
  )
  println(
    "  --badge <label:type>                      Repeatable contextual badge (label:type)"
  )
  println(
    "  --task-list <label:path>                  Repeatable task list reference (label:path)"
  )
  println(
    "  --needs-attention                         Flag indicating workflow needs human input"
  )
  println(
    "  --message <value>                         Prominent notification message"
  )
  println(
    "  --artifact <label:path[=category]>        Repeatable artifact with optional category"
  )
  println(
    "  --action <id:label:skill>                 Repeatable action (id:label:skill)"
  )
  println("  --pr-url <value>                          PR URL string")
  println(
    "  --activity <value>                        Activity state: working, waiting"
  )
  println(
    "  --workflow-type <value>                   Workflow type: agile, waterfall, diagnostic"
  )
  println(
    "  --checkpoint <phase:sha>                  Repeatable phase checkpoint (phase:sha)"
  )
  println("  --output <path>                           Output file path")
  println(
    "  --from-stdin                              Read full JSON from stdin"
  )
  println(
    "  --issue-id <value>                        Issue ID override (auto-inferred from branch)"
  )
  println(
    "  --version <value>                         Version number (default: 2)"
  )
  println(
    "  --commit                                  Stage and commit review-state.json after writing"
  )
  println()
  println("Example:")
  println(
    "  iw review-state write --display-text \"Implementing\" --display-type progress --output review-state.json"
  )

private def handleWriteStdin(argList: List[String]): Unit =
  val json = scala.io.Source.stdin.mkString

  val result = ReviewStateValidator.validate(json)
  if !result.isValid then
    Output.error("Validation failed for stdin input")
    result.errors.foreach { err =>
      Output.info(s"  ${err.field}: ${err.message}")
    }
    sys.exit(1)

  result.warnings.foreach(w => Output.warning(w))

  val outputPath = extractFlag(argList, "--output") match
    case Some(p) => os.Path(p, os.pwd)
    case None    =>
      Output.error("--output is required with --from-stdin")
      sys.exit(1)

  os.makeDir.all(outputPath / os.up)
  os.write.over(outputPath, json)

  val issueId = extractFlag(argList, "--issue-id") match
    case Some(id) => id
    case None     =>
      GitAdapter.getCurrentBranch(os.pwd).flatMap(IssueId.fromBranch) match
        case Right(id) => id.value
        case Left(_)   => "unknown"
  commitIfRequested(argList, issueId, outputPath)

  Output.success(s"Review state written to $outputPath")

private def handleWriteFlags(argList: List[String]): Unit =
  val version =
    extractFlag(argList, "--version").flatMap(_.toIntOption).getOrElse(2)

  val issueId = extractFlag(argList, "--issue-id") match
    case Some(id) => id
    case None     =>
      GitAdapter.getCurrentBranch(os.pwd).flatMap(IssueId.fromBranch) match
        case Right(id) => id.value
        case Left(err) =>
          Output.error(s"Cannot infer issue ID: $err")
          Output.info("Use --issue-id to specify explicitly")
          sys.exit(1)

  val gitSha = GitAdapter.getHeadSha(os.pwd).toOption
  val lastUpdated = java.time.Instant.now().toString

  val status = extractFlag(argList, "--status")

  val display = extractFlag(argList, "--display-text").map { text =>
    val subtext = extractFlag(argList, "--display-subtext")
    val displayType = extractFlag(argList, "--display-type") match
      case Some(t) => t
      case None    =>
        Output.error(
          "--display-type is required when --display-text is provided"
        )
        sys.exit(1)
    (text, subtext, displayType)
  }

  val badges = extractRepeatedFlag(argList, "--badge").map { raw =>
    val parts = raw.split(":", 2)
    if parts.length < 2 then
      Output.error(s"Invalid badge format '$raw', expected label:type")
      sys.exit(1)
    (parts(0), parts(1))
  }

  val taskLists = extractRepeatedFlag(argList, "--task-list").map { raw =>
    val parts = raw.split(":", 2)
    if parts.length < 2 then
      Output.error(s"Invalid task-list format '$raw', expected label:path")
      sys.exit(1)
    (parts(0), parts(1))
  }

  val needsAttention =
    if argList.contains("--needs-attention") then Some(true) else None
  val message = extractFlag(argList, "--message")
  val prUrl = extractFlag(argList, "--pr-url")
  val activity = extractFlag(argList, "--activity")
  val workflowType = extractFlag(argList, "--workflow-type")

  val artifacts = extractRepeatedFlag(argList, "--artifact").map { raw =>
    // Format: label:path or label:path=category
    val colonIdx = raw.indexOf(':')
    if colonIdx < 0 then
      Output.error(
        s"Invalid artifact format '$raw', expected label:path or label:path=category"
      )
      sys.exit(1)
    val label = raw.substring(0, colonIdx)
    val pathAndCategory = raw.substring(colonIdx + 1)
    val eqIdx = pathAndCategory.lastIndexOf('=')
    if eqIdx > 0 then
      val path = pathAndCategory.substring(0, eqIdx)
      val category = pathAndCategory.substring(eqIdx + 1)
      (label, path, Some(category))
    else (label, pathAndCategory, None)
  }

  val actions = extractRepeatedFlag(argList, "--action").map { raw =>
    val parts = raw.split(":", 3)
    if parts.length < 3 then
      Output.error(s"Invalid action format '$raw', expected id:label:skill")
      sys.exit(1)
    (parts(0), parts(1), parts(2))
  }

  val phaseCheckpoints = extractRepeatedFlag(argList, "--checkpoint").map {
    raw =>
      val parts = raw.split(":", 2)
      if parts.length < 2 then
        Output.error(s"Invalid checkpoint format '$raw', expected phase:sha")
        sys.exit(1)
      (parts(0), parts(1))
  }.toMap

  val input = ReviewStateBuilder.BuildInput(
    version = version,
    issueId = issueId,
    lastUpdated = lastUpdated,
    artifacts = artifacts,
    status = status,
    display = display,
    badges = badges,
    taskLists = taskLists,
    needsAttention = needsAttention,
    message = message,
    actions = actions,
    prUrl = prUrl,
    gitSha = gitSha,
    phaseCheckpoints = phaseCheckpoints,
    activity = activity,
    workflowType = workflowType
  )

  val json = ReviewStateBuilder.build(input)

  val validationResult = ReviewStateValidator.validate(json)
  if !validationResult.isValid then
    Output.error("Built review state failed validation")
    validationResult.errors.foreach { err =>
      Output.info(s"  ${err.field}: ${err.message}")
    }
    sys.exit(1)

  validationResult.warnings.foreach(w => Output.warning(w))

  val outputPath = extractFlag(argList, "--output") match
    case Some(p) => os.Path(p, os.pwd)
    case None    =>
      val dir = os.pwd / "project-management" / "issues" / issueId
      dir / "review-state.json"

  os.makeDir.all(outputPath / os.up)
  os.write.over(outputPath, json)

  commitIfRequested(argList, issueId, outputPath)

  Output.success(s"Review state written to $outputPath")

private def commitIfRequested(
    argList: List[String],
    issueId: String,
    outputPath: os.Path
): Unit =
  if argList.contains("--commit") then
    val status = extractFlag(argList, "--status")
    val commitMessage = status match
      case Some(s) => s"chore($issueId): update review-state to $s"
      case None    => s"chore($issueId): update review-state"
    GitAdapter
      .commitFileWithRetry(outputPath, commitMessage, outputPath / os.up)
      .left
      .foreach(err =>
        Output.warning(s"Failed to commit review-state update: $err")
      )

// ----- update subcommand -----

private def update(args: Seq[String]): Unit =
  val argList = args.toList

  if argList.contains("--help") || argList.contains("-h") then
    showUpdateHelp()
    sys.exit(0)

  // Determine input path
  val issueId = extractFlag(argList, "--issue-id") match
    case Some(id) => id
    case None     =>
      GitAdapter.getCurrentBranch(os.pwd).flatMap(IssueId.fromBranch) match
        case Right(id) => id.value
        case Left(err) =>
          Output.error(s"Cannot infer issue ID: $err")
          Output.info("Use --issue-id to specify explicitly")
          sys.exit(1)

  val inputPath = extractFlag(argList, "--input") match
    case Some(p) => os.Path(p, os.pwd)
    case None    =>
      val dir = os.pwd / "project-management" / "issues" / issueId
      dir / "review-state.json"

  if !os.exists(inputPath) then
    Output.error(s"Review state file not found: $inputPath")
    Output.info("Use --input to specify a different path")
    sys.exit(1)

  // Read existing state
  val existingJson = os.read(inputPath)

  // Build update input
  val status = extractFlag(argList, "--status")
  val clearStatus = argList.contains("--clear-status")

  val displayText = extractFlag(argList, "--display-text")
  val displaySubtext = extractFlag(argList, "--display-subtext")
  val displayType = extractFlag(argList, "--display-type")
  val clearDisplay = argList.contains("--clear-display")
  val clearDisplaySubtext = argList.contains("--clear-display-subtext")

  val message = extractFlag(argList, "--message")
  val clearMessage = argList.contains("--clear-message")

  val needsAttention =
    if argList.contains("--needs-attention") then Some(true) else None
  val clearNeedsAttention = argList.contains("--clear-needs-attention")

  val prUrl = extractFlag(argList, "--pr-url")
  val clearPrUrl = argList.contains("--clear-pr-url")

  val activity = extractFlag(argList, "--activity")
  val clearActivity = argList.contains("--clear-activity")

  val workflowType = extractFlag(argList, "--workflow-type")
  val clearWorkflowType = argList.contains("--clear-workflow-type")

  val gitSha = extractFlag(argList, "--git-sha")

  // Parse array fields with mode detection
  val (artifacts, artifactsMode) = parseArrayField(
    argList,
    "--artifact",
    "--append-artifact",
    "--clear-artifacts"
  ) { raw =>
    val colonIdx = raw.indexOf(':')
    if colonIdx < 0 then
      Output.error(
        s"Invalid artifact format '$raw', expected label:path or label:path=category"
      )
      sys.exit(1)
    val label = raw.substring(0, colonIdx)
    val pathAndCategory = raw.substring(colonIdx + 1)
    val eqIdx = pathAndCategory.lastIndexOf('=')
    if eqIdx > 0 then
      val path = pathAndCategory.substring(0, eqIdx)
      val category = pathAndCategory.substring(eqIdx + 1)
      (label, path, Some(category))
    else (label, pathAndCategory, None)
  }

  val (badges, badgesMode) =
    parseArrayField(argList, "--badge", "--append-badge", "--clear-badges") {
      raw =>
        val parts = raw.split(":", 2)
        if parts.length < 2 then
          Output.error(s"Invalid badge format '$raw', expected label:type")
          sys.exit(1)
        (parts(0), parts(1))
    }

  val (taskLists, taskListsMode) = parseArrayField(
    argList,
    "--task-list",
    "--append-task-list",
    "--clear-task-lists"
  ) { raw =>
    val parts = raw.split(":", 2)
    if parts.length < 2 then
      Output.error(s"Invalid task-list format '$raw', expected label:path")
      sys.exit(1)
    (parts(0), parts(1))
  }

  val (actions, actionsMode) =
    parseArrayField(argList, "--action", "--append-action", "--clear-actions") {
      raw =>
        val parts = raw.split(":", 3)
        if parts.length < 3 then
          Output.error(s"Invalid action format '$raw', expected id:label:skill")
          sys.exit(1)
        (parts(0), parts(1), parts(2))
    }

  val (phaseCheckpoints, phaseCheckpointsMode) = parseArrayField(
    argList,
    "--checkpoint",
    "--append-checkpoint",
    "--clear-checkpoints"
  ) { raw =>
    val parts = raw.split(":", 2)
    if parts.length < 2 then
      Output.error(s"Invalid checkpoint format '$raw', expected phase:sha")
      sys.exit(1)
    (parts(0), parts(1))
  }

  val updateInput = ReviewStateUpdater.UpdateInput(
    status = status,
    message = message,
    needsAttention = needsAttention,
    prUrl = prUrl,
    activity = activity,
    workflowType = workflowType,
    gitSha = gitSha,
    displayText = displayText,
    displaySubtext = displaySubtext,
    displayType = displayType,
    clearDisplaySubtext = clearDisplaySubtext,
    artifacts = artifacts,
    artifactsMode = artifactsMode,
    badges = badges,
    badgesMode = badgesMode,
    taskLists = taskLists,
    taskListsMode = taskListsMode,
    actions = actions,
    actionsMode = actionsMode,
    phaseCheckpoints = phaseCheckpoints.map(_.toMap),
    phaseCheckpointsMode = phaseCheckpointsMode,
    clearStatus = clearStatus,
    clearMessage = clearMessage,
    clearNeedsAttention = clearNeedsAttention,
    clearPrUrl = clearPrUrl,
    clearDisplay = clearDisplay,
    clearActivity = clearActivity,
    clearWorkflowType = clearWorkflowType
  )

  // Merge updates
  val mergedJson = ReviewStateUpdater.merge(existingJson, updateInput)

  // Validate merged result
  val validationResult = ReviewStateValidator.validate(mergedJson)
  if !validationResult.isValid then
    Output.error("Updated review state failed validation")
    validationResult.errors.foreach { err =>
      Output.info(s"  ${err.field}: ${err.message}")
    }
    sys.exit(1)

  validationResult.warnings.foreach(w => Output.warning(w))

  // Write back to same location
  os.write.over(inputPath, mergedJson)

  commitIfRequested(argList, issueId, inputPath)

  Output.success(s"Review state updated at $inputPath")

private def showUpdateHelp(): Unit =
  println("Update an existing review-state.json file with partial changes")
  println()
  println("Usage:")
  println("  iw review-state update [options]")
  println()
  println("Options:")
  println(
    "  --status <value>                     Optional machine-readable status identifier"
  )
  println(
    "  --display-text <value>               Primary display text for status badge"
  )
  println(
    "  --display-subtext <value>            Optional secondary display text"
  )
  println(
    "  --display-type <value>               Display type: info, success, warning, error, progress"
  )
  println(
    "  --badge <label:type>                 Repeatable contextual badge (label:type)"
  )
  println(
    "  --append-badge <label:type>          Append badge to existing badges"
  )
  println("  --clear-badges                       Remove all badges")
  println(
    "  --task-list <label:path>             Repeatable task list reference (label:path)"
  )
  println("  --append-task-list <label:path>      Append task list to existing")
  println("  --clear-task-lists                   Remove all task lists")
  println(
    "  --needs-attention                    Flag indicating workflow needs human input"
  )
  println(
    "  --message <value>                    Prominent notification message"
  )
  println(
    "  --artifact <label:path[=category]>   Repeatable artifact with optional category"
  )
  println(
    "  --append-artifact <label:path[=category]>  Append artifact to existing"
  )
  println("  --clear-artifacts                    Remove all artifacts")
  println(
    "  --action <id:label:skill>            Repeatable action (id:label:skill)"
  )
  println("  --append-action <id:label:skill>     Append action to existing")
  println("  --clear-actions                      Remove all actions")
  println("  --pr-url <value>                     PR URL string")
  println(
    "  --checkpoint <phase:sha>             Repeatable phase checkpoint (phase:sha)"
  )
  println(
    "  --append-checkpoint <phase:sha>      Append checkpoint to existing"
  )
  println("  --clear-checkpoints                  Remove all checkpoints")
  println("  --git-sha <value>                    Override git SHA")
  println(
    "  --input <path>                       Input file path (default: auto-detect from issue_id)"
  )
  println(
    "  --activity <value>                   Activity state: working, waiting"
  )
  println("  --clear-activity                     Remove activity field")
  println(
    "  --workflow-type <value>              Workflow type: agile, waterfall, diagnostic"
  )
  println("  --clear-workflow-type                Remove workflow_type field")
  println("  --clear-status                       Remove status field")
  println("  --clear-message                      Remove message field")
  println("  --clear-pr-url                       Remove pr_url field")
  println("  --clear-display                      Remove display field")
  println("  --clear-display-subtext              Remove display.subtext field")
  println("  --clear-needs-attention              Remove needs_attention field")
  println(
    "  --issue-id <value>                   Issue ID override (auto-inferred from branch)"
  )
  println(
    "  --commit                             Stage and commit review-state.json after writing"
  )
  println()
  println("Examples:")
  println("  iw review-state update --display-text \"Implementing\"")
  println(
    "  iw review-state update --append-artifact \"Tasks:phase-02-tasks.md\""
  )
  println("  iw review-state update --clear-message")

// ----- shared helpers -----

private def extractFlag(args: List[String], flag: String): Option[String] =
  val idx = args.indexOf(flag)
  if idx >= 0 && idx + 1 < args.length then Some(args(idx + 1))
  else None

private def extractRepeatedFlag(
    args: List[String],
    flag: String
): List[String] =
  args
    .sliding(2)
    .collect {
      case List(`flag`, value) if !value.startsWith("--") => value
    }
    .toList

private def parseArrayField[T](
    args: List[String],
    replaceFlag: String,
    appendFlag: String,
    clearFlag: String
)(parse: String => T): (Option[List[T]], ReviewStateUpdater.ArrayMergeMode) =
  val clearMode = args.contains(clearFlag)
  val replaceValues = extractRepeatedFlag(args, replaceFlag)
  val appendValues = extractRepeatedFlag(args, appendFlag)

  // Some(Nil) signals "process this field but produce an empty result"
  if clearMode then (Some(Nil), ReviewStateUpdater.ArrayMergeMode.Clear)
  else if replaceValues.nonEmpty then
    (Some(replaceValues.map(parse)), ReviewStateUpdater.ArrayMergeMode.Replace)
  else if appendValues.nonEmpty then
    (Some(appendValues.map(parse)), ReviewStateUpdater.ArrayMergeMode.Append)
  else (None, ReviewStateUpdater.ArrayMergeMode.Replace)
