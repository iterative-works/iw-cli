// PURPOSE: Initialize iw-cli configuration for the project
// USAGE: iw init [--force] [--tracker=...] [--team=...] [--repository=...] [--team-prefix=...] [--base-url=...]

import iw.core.commands.{Init, LiveCommandEnv}

@main def init(args: String*): Unit =
  sys.exit(Init.run(args, LiveCommandEnv.default).exitCode)
