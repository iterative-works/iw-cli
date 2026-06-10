// PURPOSE: Check system dependencies and configuration
// USAGE: iw doctor [--env|--quality|--fix]

import iw.core.commands.{Doctor, LiveCommandEnv}

@main def doctor(args: String*): Unit =
  sys.exit(Doctor.run(args, LiveCommandEnv.default).exitCode)
