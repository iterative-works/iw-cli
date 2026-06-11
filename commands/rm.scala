// PURPOSE: Remove a worktree for a completed issue (kills tmux session with safety checks)
// USAGE: iw rm <issue-id> [--force]

import iw.core.commands.{LiveCommandEnv, Rm}

@main def rm(args: String*): Unit =
  sys.exit(Rm.run(args, LiveCommandEnv.default).exitCode)
