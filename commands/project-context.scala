// PURPOSE: Output a deterministic project context fragment for AI agent prompts
// USAGE: iw project-context

import iw.core.commands.{LiveCommandEnv, ProjectContext}

@main def projectContext(args: String*): Unit =
  sys.exit(ProjectContext.run(args, LiveCommandEnv.default).exitCode)
