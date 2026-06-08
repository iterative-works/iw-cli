// PURPOSE: Shows detailed status for a specific worktree
// USAGE: iw status [issue-id] [--json]

import iw.core.commands.{LiveCommandEnv, Status}

@main def status(args: String*): Unit =
  sys.exit(Status.run(args, LiveCommandEnv.default).exitCode)
