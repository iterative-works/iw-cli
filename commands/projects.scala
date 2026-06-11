// PURPOSE: Lists all registered projects from server state
// USAGE: iw projects [--json]

import iw.core.commands.{LiveCommandEnv, Projects}

@main def projects(args: String*): Unit =
  sys.exit(Projects.run(args, LiveCommandEnv.default).exitCode)
