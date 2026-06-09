// PURPOSE: Creates an isolated worktree for a specific issue with a tmux session
// USAGE: iw start [--prompt <text>] <issue-id>

import iw.core.commands.{LiveCommandEnv, Start}

@main def start(args: String*): Unit =
  sys.exit(Start.run(args, LiveCommandEnv.default).exitCode)
