// PURPOSE: Write a validated review-state.json file from CLI flags or stdin
// PURPOSE: Auto-populates issue_id, git_sha, and last_updated from git context
// USAGE: iw review-state write [options]
// USAGE: iw review-state write --from-stdin --output <path>
// ARGS:
//   --status <value>           Optional machine-readable status identifier
//   --display-text <value>     Primary display text for status badge
//   --display-subtext <value>  Optional secondary display text
//   --display-type <value>     Display type: info, success, warning, error, progress
//   --badge <label:type>       Repeatable contextual badge (label:type)
//   --task-list <label:path>   Repeatable task list reference (label:path)
//   --needs-attention          Flag indicating workflow needs human input
//   --message <value>          Prominent notification message
//   --artifact <label:path[=category]>  Repeatable artifact with optional category
//   --action <id:label:skill>  Repeatable action (id:label:skill)
//   --pr-url <value>           PR URL string
//   --activity <value>         Activity state: working, waiting
//   --workflow-type <value>    Workflow type: agile, waterfall, diagnostic
//   --checkpoint <phase:sha>   Repeatable phase checkpoint (phase:sha)
//   --output <path>            Output file path
//   --from-stdin               Read full JSON from stdin
//   --issue-id <value>         Issue ID override (auto-inferred from branch)
//   --version <value>          Version number (default: 2)
//   --commit                   Stage and commit review-state.json after writing
// EXAMPLE: iw review-state write --display-text "Implementing" --display-type progress --output review-state.json

import iw.core.model.*
import iw.core.adapters.*
import iw.core.output.*

@main def write(args: String*): Unit =
  val argList = args.toList

  // Handle --help / -h flag
  if argList.contains("--help") || argList.contains("-h") then
    showHelp()
    sys.exit(0)

  if argList.contains("--from-stdin") then handleStdin(argList)
  else handleFlags(argList)

private def showHelp(): Unit =
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

private def handleStdin(argList: List[String]): Unit =
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

private def handleFlags(argList: List[String]): Unit =
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
