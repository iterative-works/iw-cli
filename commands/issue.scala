// PURPOSE: Fetch and display issue information from the tracker
// USAGE: iw issue [issue-id] | iw issue create --title "..." [--description "..."]

import iw.core.commands.{Issue, LiveCommandEnv}

@main def issue(args: String*): Unit =
  sys.exit(Issue.run(args, LiveCommandEnv.default).exitCode)
