// PURPOSE: Output a deterministic project context fragment for AI agent prompts
// PURPOSE: Describes forge type, CLI tools, and project identity so agents know which tools to use
// USAGE: iw project-context
// EXAMPLE: iw project-context

import iw.core.model.*
import iw.core.adapters.*
import iw.core.output.*

@main def projectContext(args: String*): Unit =
  val cwd = os.pwd

  val configPath = cwd / Constants.Paths.IwDir / "config.conf"
  val config = ConfigFileRepository.read(configPath) match
    case Some(c) => c
    case None    =>
      Output.error("Configuration file not found. Run 'iw init' first.")
      sys.exit(1)

  val remoteOpt = GitAdapter.getRemoteUrl(cwd)
  val input = ProjectContext.fromConfig(config, remoteOpt)
  print(ProjectContext.render(input))
