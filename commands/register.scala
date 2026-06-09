// PURPOSE: Registers current directory (worktree or project) with the dashboard server
// USAGE: iw register

import iw.core.commands.{LiveCommandEnv, Register}

@main def register(args: String*): Unit =
  sys.exit(Register.run(args, LiveCommandEnv.default).exitCode)
