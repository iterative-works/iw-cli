// PURPOSE: Write a validated review-state.json file from CLI flags or stdin
// PURPOSE: Auto-populates issue_id, git_sha, and last_updated from git context
// USAGE: iw write-review-state --status <value> [options]
// USAGE: iw write-review-state --from-stdin --output <path>
// ARGS:
//   --status <value>        Status string (required unless --from-stdin)
//   --phase <value>         Phase (integer or string)
//   --step <value>          Step string
//   --message <value>       Message string
//   --artifact <label:path> Repeatable, colon-separated label:path
//   --action <id:label:skill> Repeatable, colon-separated
//   --branch <value>        Branch override (auto-detected from git)
//   --batch-mode            Boolean flag (presence = true)
//   --pr-url <value>        PR URL string
//   --output <path>         Output file path
//   --from-stdin            Read full JSON from stdin
//   --issue-id <value>      Issue ID override (auto-inferred from branch)
//   --version <value>       Version number (default: 1)
// EXAMPLE: iw write-review-state --status implementing --phase 2 --output review-state.json

import iw.core.model.*
import iw.core.adapters.*
import iw.core.output.*

@main def `write-review-state`(args: String*): Unit =
  val argList = args.toList

  if argList.contains("--from-stdin") then
    handleStdin(argList)
  else
    handleFlags(argList)

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
    case None =>
      Output.error("--output is required with --from-stdin")
      sys.exit(1)

  os.makeDir.all(outputPath / os.up)
  os.write.over(outputPath, json)
  Output.success(s"Review state written to $outputPath")

private def handleFlags(argList: List[String]): Unit =
  val status = extractFlag(argList, "--status") match
    case Some(s) => s
    case None =>
      Output.error("--status is required")
      sys.exit(1)

  val version = extractFlag(argList, "--version").flatMap(_.toIntOption).getOrElse(1)

  val issueId = extractFlag(argList, "--issue-id") match
    case Some(id) => id
    case None =>
      GitAdapter.getCurrentBranch(os.pwd).flatMap(IssueId.fromBranch) match
        case Right(id) => id.value
        case Left(err) =>
          Output.error(s"Cannot infer issue ID: $err")
          Output.info("Use --issue-id to specify explicitly")
          sys.exit(1)

  val branch = extractFlag(argList, "--branch").orElse(
    GitAdapter.getCurrentBranch(os.pwd).toOption
  )

  val gitSha = GitAdapter.getHeadSha(os.pwd).toOption

  val lastUpdated = java.time.Instant.now().toString

  val phase = extractFlag(argList, "--phase").map { v =>
    v.toIntOption match
      case Some(n) => Left(n)
      case None    => Right(v)
  }

  val step = extractFlag(argList, "--step")
  val message = extractFlag(argList, "--message")
  val prUrl = extractFlag(argList, "--pr-url")
  val batchMode = if argList.contains("--batch-mode") then Some(true) else None

  val artifacts = extractRepeatedFlag(argList, "--artifact").map { raw =>
    val colonIdx = raw.indexOf(':')
    if colonIdx < 0 then
      Output.error(s"Invalid artifact format '$raw', expected label:path")
      sys.exit(1)
    (raw.substring(0, colonIdx), raw.substring(colonIdx + 1))
  }

  val actions = extractRepeatedFlag(argList, "--action").map { raw =>
    val parts = raw.split(":", 3)
    if parts.length < 3 then
      Output.error(s"Invalid action format '$raw', expected id:label:skill")
      sys.exit(1)
    (parts(0), parts(1), parts(2))
  }

  val phaseCheckpoints = extractRepeatedFlag(argList, "--checkpoint").map { raw =>
    val parts = raw.split(":", 2)
    if parts.length < 2 then
      Output.error(s"Invalid checkpoint format '$raw', expected phase:sha")
      sys.exit(1)
    (parts(0), parts(1))
  }.toMap

  val input = ReviewStateBuilder.BuildInput(
    version = version,
    issueId = issueId,
    status = status,
    lastUpdated = lastUpdated,
    artifacts = artifacts,
    phase = phase,
    step = step,
    branch = branch,
    prUrl = prUrl,
    gitSha = gitSha,
    message = message,
    batchMode = batchMode,
    phaseCheckpoints = phaseCheckpoints,
    actions = actions
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
    case None =>
      val dir = os.pwd / "project-management" / "issues" / issueId
      dir / "review-state.json"

  os.makeDir.all(outputPath / os.up)
  os.write.over(outputPath, json)
  Output.success(s"Review state written to $outputPath")

private def extractFlag(args: List[String], flag: String): Option[String] =
  val idx = args.indexOf(flag)
  if idx >= 0 && idx + 1 < args.length then Some(args(idx + 1))
  else None

private def extractRepeatedFlag(args: List[String], flag: String): List[String] =
  args.sliding(2).collect {
    case List(`flag`, value) if !value.startsWith("--") => value
  }.toList
