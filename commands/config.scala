// PURPOSE: Query and export project configuration values
// USAGE: iw config get <field> | iw config --json

import iw.core.commands.{Config, LiveCommandEnv}

@main def config(args: String*): Unit =
  sys.exit(Config.run(args, LiveCommandEnv.default).exitCode)
