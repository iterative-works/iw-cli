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

import iw.core.output.*
import scala.util.Try

@main def `review-state`(args: String*): Unit =
  if args.isEmpty then
    Output.error("No subcommand provided")
    Output.info("Usage: iw review-state <subcommand> [args...]")
    Output.info("Available subcommands:")
    Output.info("  validate <path>    Validate review-state.json file")
    Output.info("  write [options]    Create new review-state.json from scratch")
    Output.info("  update [options]   Update existing review-state.json")
    sys.exit(1)

  val subcommand = args.head
  val subcommandArgs = args.tail

  subcommand match
    case "validate" =>
      runSubcommand("validate", subcommandArgs)
    case "write" =>
      runSubcommand("write", subcommandArgs)
    case "update" =>
      runSubcommand("update", subcommandArgs)
    case _ =>
      Output.error(s"Unknown subcommand: $subcommand")
      Output.info("Available subcommands: validate, write, update")
      sys.exit(1)

private def runSubcommand(name: String, args: Seq[String]): Unit =
  // Get the commands and core directories from environment (set by iw bootstrap script)
  val commandsDir = sys.env.get("IW_COMMANDS_DIR") match
    case Some(dir) => os.Path(dir)
    case None =>
      Output.error("IW_COMMANDS_DIR environment variable not set")
      sys.exit(1)

  val coreDir = sys.env.get("IW_CORE_DIR") match
    case Some(dir) => os.Path(dir)
    case None =>
      Output.error("IW_CORE_DIR environment variable not set")
      sys.exit(1)

  val scriptPath = commandsDir / "review-state" / s"$name.scala"

  if !os.exists(scriptPath) then
    Output.error(s"Subcommand script not found: $scriptPath")
    sys.exit(1)

  // Find all core files excluding test directory (same as iw-run does)
  val coreFiles = os.walk(coreDir)
    .filter(p => p.ext == "scala" && !p.segments.contains("test"))
    .map(_.toString)

  // Run the subcommand script using scala-cli with core files (like iw-run does)
  val command = List("scala-cli", "run", "-q", "--suppress-outdated-dependency-warning", scriptPath.toString) ++
                coreFiles ++
                List("--") ++
                args
  val result = os.proc(command).call(
    cwd = os.pwd,
    stdin = os.Inherit,
    stdout = os.Inherit,
    stderr = os.Inherit,
    check = false
  )

  sys.exit(result.exitCode)
