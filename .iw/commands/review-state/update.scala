// PURPOSE: Update an existing review-state.json file with partial changes
// PURPOSE: Merges provided updates with existing state, validates, and writes back
// USAGE: iw review-state update [options]
// ARGS:
//   --status <value>           Optional machine-readable status identifier
//   --display-text <value>     Primary display text for status badge
//   --display-subtext <value>  Optional secondary display text
//   --display-type <value>     Display type: info, success, warning, error, progress
//   --badge <label:type>       Repeatable contextual badge (label:type)
//   --append-badge <label:type>  Append badge to existing badges
//   --clear-badges             Remove all badges
//   --task-list <label:path>   Repeatable task list reference (label:path)
//   --append-task-list <label:path>  Append task list to existing
//   --clear-task-lists         Remove all task lists
//   --needs-attention          Flag indicating workflow needs human input
//   --message <value>          Prominent notification message
//   --artifact <label:path[=category]>  Repeatable artifact with optional category
//   --append-artifact <label:path[=category]>  Append artifact to existing
//   --clear-artifacts          Remove all artifacts
//   --action <id:label:skill>  Repeatable action (id:label:skill)
//   --append-action <id:label:skill>  Append action to existing
//   --clear-actions            Remove all actions
//   --pr-url <value>           PR URL string
//   --checkpoint <phase:sha>   Repeatable phase checkpoint (phase:sha)
//   --append-checkpoint <phase:sha>  Append checkpoint to existing
//   --clear-checkpoints        Remove all checkpoints
//   --git-sha <value>          Override git SHA
//   --input <path>             Input file path (default: auto-detect from issue_id)
//   --clear-status             Remove status field
//   --clear-message            Remove message field
//   --clear-pr-url             Remove pr_url field
//   --clear-display            Remove display field
//   --clear-display-subtext    Remove display.subtext field
//   --clear-needs-attention    Remove needs_attention field
//   --issue-id <value>         Issue ID override (auto-inferred from branch)
// EXAMPLE: iw review-state update --display-text "Implementing"
// EXAMPLE: iw review-state update --append-artifact "Tasks:phase-02-tasks.md"
// EXAMPLE: iw review-state update --clear-message

import iw.core.model.*
import iw.core.adapters.*
import iw.core.output.*

@main def update(args: String*): Unit =
  val argList = args.toList

  // Determine input path
  val issueId = extractFlag(argList, "--issue-id") match
    case Some(id) => id
    case None =>
      GitAdapter.getCurrentBranch(os.pwd).flatMap(IssueId.fromBranch) match
        case Right(id) => id.value
        case Left(err) =>
          Output.error(s"Cannot infer issue ID: $err")
          Output.info("Use --issue-id to specify explicitly")
          sys.exit(1)

  val inputPath = extractFlag(argList, "--input") match
    case Some(p) => os.Path(p, os.pwd)
    case None =>
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

  val needsAttention = if argList.contains("--needs-attention") then Some(true) else None
  val clearNeedsAttention = argList.contains("--clear-needs-attention")

  val prUrl = extractFlag(argList, "--pr-url")
  val clearPrUrl = argList.contains("--clear-pr-url")

  val gitSha = extractFlag(argList, "--git-sha")

  // Parse array fields with mode detection
  val (artifacts, artifactsMode) = parseArrayField(argList, "--artifact", "--append-artifact", "--clear-artifacts") { raw =>
    val colonIdx = raw.indexOf(':')
    if colonIdx < 0 then
      Output.error(s"Invalid artifact format '$raw', expected label:path or label:path=category")
      sys.exit(1)
    val label = raw.substring(0, colonIdx)
    val pathAndCategory = raw.substring(colonIdx + 1)
    val eqIdx = pathAndCategory.lastIndexOf('=')
    if eqIdx > 0 then
      val path = pathAndCategory.substring(0, eqIdx)
      val category = pathAndCategory.substring(eqIdx + 1)
      (label, path, Some(category))
    else
      (label, pathAndCategory, None)
  }

  val (badges, badgesMode) = parseArrayField(argList, "--badge", "--append-badge", "--clear-badges") { raw =>
    val parts = raw.split(":", 2)
    if parts.length < 2 then
      Output.error(s"Invalid badge format '$raw', expected label:type")
      sys.exit(1)
    (parts(0), parts(1))
  }

  val (taskLists, taskListsMode) = parseArrayField(argList, "--task-list", "--append-task-list", "--clear-task-lists") { raw =>
    val parts = raw.split(":", 2)
    if parts.length < 2 then
      Output.error(s"Invalid task-list format '$raw', expected label:path")
      sys.exit(1)
    (parts(0), parts(1))
  }

  val (actions, actionsMode) = parseArrayField(argList, "--action", "--append-action", "--clear-actions") { raw =>
    val parts = raw.split(":", 3)
    if parts.length < 3 then
      Output.error(s"Invalid action format '$raw', expected id:label:skill")
      sys.exit(1)
    (parts(0), parts(1), parts(2))
  }

  val (phaseCheckpoints, phaseCheckpointsMode) = parseArrayField(argList, "--checkpoint", "--append-checkpoint", "--clear-checkpoints") { raw =>
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
    clearDisplay = clearDisplay
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
  Output.success(s"Review state updated at $inputPath")

private def extractFlag(args: List[String], flag: String): Option[String] =
  val idx = args.indexOf(flag)
  if idx >= 0 && idx + 1 < args.length then Some(args(idx + 1))
  else None

private def extractRepeatedFlag(args: List[String], flag: String): List[String] =
  args.sliding(2).collect {
    case List(`flag`, value) if !value.startsWith("--") => value
  }.toList

private def parseArrayField[T](
  args: List[String],
  replaceFlag: String,
  appendFlag: String,
  clearFlag: String
)(parse: String => T): (Option[List[T]], ReviewStateUpdater.ArrayMergeMode) =
  val clearMode = args.contains(clearFlag)
  val replaceValues = extractRepeatedFlag(args, replaceFlag)
  val appendValues = extractRepeatedFlag(args, appendFlag)

  if clearMode then
    (Some(Nil), ReviewStateUpdater.ArrayMergeMode.Clear)  // Use Some(Nil) to trigger processing
  else if replaceValues.nonEmpty then
    (Some(replaceValues.map(parse)), ReviewStateUpdater.ArrayMergeMode.Replace)
  else if appendValues.nonEmpty then
    (Some(appendValues.map(parse)), ReviewStateUpdater.ArrayMergeMode.Append)
  else
    (None, ReviewStateUpdater.ArrayMergeMode.Replace)
