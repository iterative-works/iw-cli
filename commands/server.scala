// PURPOSE: Manage iw dashboard server lifecycle (start/stop/status)
// USAGE: iw server <start|stop|status>

import iw.core.commands.{LiveCommandEnv, Server}

@main def server(args: String*): Unit =
  sys.exit(Server.run(args, LiveCommandEnv.default).exitCode)
