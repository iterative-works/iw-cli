// PURPOSE: Main dispatcher for review-state subcommands (validate/write/update)
// USAGE: iw review-state <subcommand> [args...]

import iw.core.commands.{LiveCommandEnv, ReviewState}

@main def `review-state`(args: String*): Unit =
  sys.exit(ReviewState.run(args, LiveCommandEnv.default).exitCode)
