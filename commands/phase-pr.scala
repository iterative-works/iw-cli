// PURPOSE: Pushes the phase sub-branch and creates a GitHub/GitLab PR or MR
// USAGE: iw phase-pr --title TITLE [--body BODY] [--batch] [--issue-id ID] [--phase-number N]

import iw.core.commands.{LiveCommandEnv, PhasePr}

@main def phasePr(args: String*): Unit =
  sys.exit(PhasePr.run(args, LiveCommandEnv.default).exitCode)
