// PURPOSE: ProjectContext command logic: read project config + remote, render context fragment
// PURPOSE: All I/O via CommandEnv so the body is testable in-VM

package iw.core.commands

import iw.core.model.{
  ConfigSerializer,
  Constants,
  ProjectContext as ProjectContextModel
}

object ProjectContext:
  def run(args: Seq[String], env: CommandEnv): CommandResult =
    val configPath = env.cwd / Constants.Paths.IwDir / "config.conf"
    if !env.fs.exists(configPath) then
      env.console.err(
        "Error: Configuration file not found. Run 'iw init' first."
      )
      CommandResult.error
    else
      val outcome = for
        hocon <- env.fs.read(configPath)
        config <- ConfigSerializer.fromHocon(hocon)
      yield ProjectContextModel.render(
        ProjectContextModel.fromConfig(config, env.git.getRemoteUrl(env.cwd))
      )
      outcome match
        case Left(err) =>
          env.console.err(s"Error: $err")
          CommandResult.error
        case Right(rendered) =>
          rendered.split("\n", -1).foreach(env.console.out)
          CommandResult.ok
