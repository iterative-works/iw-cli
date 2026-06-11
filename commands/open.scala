// PURPOSE: Opens an existing worktree tmux session, creating session if needed
// USAGE: iw open [--prompt <text>] [issue-id]

import iw.core.commands.{LiveCommandEnv, Open}

@main def open(args: String*): Unit =
  sys.exit(Open.run(args, LiveCommandEnv.default).exitCode)
