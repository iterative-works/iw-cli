// PURPOSE: analyze command logic: delegate to `iw start --prompt /iterative-works:triage-issue <id>`
// PURPOSE: Process.runInteractive carries the user's terminal through; iw-run path supplied by shim

package iw.core.commands

object Analyze:
  def run(args: Seq[String], env: CommandEnv, iwRun: String): CommandResult =
    if args.isEmpty then
      env.console.err("Error: Missing issue ID")
      env.console.out("Usage: iw analyze <issue-id>")
      CommandResult.error
    else
      val issueId = args.head
      val exitCode = env.process.runInteractive(
        Seq(
          iwRun,
          "start",
          "--prompt",
          "/iterative-works:triage-issue",
          issueId
        )
      )
      CommandResult(exitCode)
