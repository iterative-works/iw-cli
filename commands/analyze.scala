// PURPOSE: Shortcut command for triaging an issue with the Claude triage agent
// PURPOSE: Delegates to `iw start <issueId> --prompt "/iterative-works:triage-issue"`

import iw.core.adapters.*
import iw.core.output.*

@main def analyze(args: String*): Unit =
  if args.isEmpty then
    Output.error("Missing issue ID")
    Output.info("Usage: iw analyze <issue-id>")
    sys.exit(1)

  val issueId = args.head
  val iwRun = findIwRun()
  val exitCode = ProcessAdapter.runInteractive(
    Seq(iwRun, "start", "--prompt", "/iterative-works:triage-issue", issueId)
  )
  if exitCode != 0 then sys.exit(exitCode)

// Find iw-run relative to IW_COMMANDS_DIR (set by the iw launcher).
// commands/ is always one level below the installation root.
private def findIwRun(): String =
  sys.env.get("IW_COMMANDS_DIR") match
    case None =>
      Output.error("IW_COMMANDS_DIR not set")
      sys.exit(1)
    case Some(dir) =>
      val commandsDir = os.Path(dir)
      val iwRun = commandsDir / os.up / "iw-run"
      if os.exists(iwRun) then iwRun.toString
      else { Output.error("Cannot find iw-run"); sys.exit(1) }
