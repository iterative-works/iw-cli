// PURPOSE: Commits staged changes with a structured message and updates phase task file
// USAGE: iw phase-commit --title TITLE [--items ITEM1,ITEM2,...] [--issue-id ID] [--phase-number N]

import iw.core.commands.{LiveCommandEnv, PhaseCommit}

@main def phaseCommit(args: String*): Unit =
  sys.exit(PhaseCommit.run(args, LiveCommandEnv.default).exitCode)
