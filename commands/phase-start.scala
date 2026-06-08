// PURPOSE: Creates a phase sub-branch from a feature branch and records baseline SHA
// USAGE: iw phase-start <phase-number> [--issue-id ID]

import iw.core.commands.{LiveCommandEnv, PhaseStart}

@main def phaseStart(args: String*): Unit =
  sys.exit(PhaseStart.run(args, LiveCommandEnv.default).exitCode)
