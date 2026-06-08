// PURPOSE: Shortcut command for triaging an issue with the Claude triage agent
// USAGE: iw analyze <issue-id>

import iw.core.commands.{Analyze, LiveCommandEnv}
import iw.core.output.Output

@main def analyze(args: String*): Unit =
  val iwRun = findIwRun()
  sys.exit(Analyze.run(args, LiveCommandEnv.default, iwRun).exitCode)

private def findIwRun(): String =
  sys.env.get("IW_COMMANDS_DIR") match
    case None =>
      Output.error("IW_COMMANDS_DIR not set")
      sys.exit(1)
    case Some(dir) =>
      val iwRun = os.Path(dir) / os.up / "iw-run"
      if os.exists(iwRun) then iwRun.toString
      else { Output.error("Cannot find iw-run"); sys.exit(1) }
