// PURPOSE: Lists worktrees for current project or all projects
// USAGE: iw worktrees [--all] [--json]

import iw.core.commands.{LiveCommandEnv, Worktrees}

@main def worktrees(args: String*): Unit =
  sys.exit(Worktrees.run(args, LiveCommandEnv.default).exitCode)
