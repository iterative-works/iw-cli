// PURPOSE: Start the iw dashboard server and open it in a browser
// USAGE: iw dashboard [--state-path <path>] [--sample-data] [--dev] [--help]

import iw.core.commands.{Dashboard, LiveCommandEnv}

@main def dashboard(args: String*): Unit =
  sys.exit(Dashboard.run(args, LiveCommandEnv.default).exitCode)
