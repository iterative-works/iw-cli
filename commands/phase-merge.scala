// PURPOSE: Polls CI checks for a PR/MR and auto-merges on success (supports GitHub and GitLab)
// USAGE: iw phase-merge [--timeout DUR] [--poll-interval DUR] [--max-retries N] [--issue-id ID] [--phase-number N]

import iw.core.commands.{LiveCommandEnv, PhaseMerge}

@main def phaseMerge(args: String*): Unit =
  sys.exit(PhaseMerge.run(args, LiveCommandEnv.default).exitCode)
