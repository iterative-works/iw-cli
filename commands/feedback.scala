// PURPOSE: Submit feedback to iw-cli team via GitHub issues
// USAGE: iw feedback "Issue title" [--description "Details"] [--type bug|feature]

import iw.core.commands.{Feedback, LiveCommandEnv}

@main def feedback(args: String*): Unit =
  sys.exit(Feedback.run(args, LiveCommandEnv.default).exitCode)
