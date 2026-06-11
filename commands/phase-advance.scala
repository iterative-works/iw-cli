// PURPOSE: Verifies a phase PR is merged then advances the feature branch to match remote
// USAGE: iw phase-advance [--issue-id ID] [--phase-number N]

import iw.core.commands.{LiveCommandEnv, PhaseAdvance}

@main def phaseAdvance(args: String*): Unit =
  sys.exit(PhaseAdvance.run(args, LiveCommandEnv.default).exitCode)
