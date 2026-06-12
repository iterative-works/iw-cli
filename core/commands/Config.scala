// PURPOSE: Config command logic: get/json/usage. Reads .iw/config.conf and exposes
// PURPOSE: structured access with backward-compatible flat aliases over nested fields

package iw.core.commands

import iw.core.model.{Constants, ProjectConfigurationJson}

import scala.util.{Failure, Success, Try}

object Config:
  def run(args: Seq[String], env: CommandEnv): CommandResult =
    args.toList match
      case "get" :: field :: Nil => handleGet(field, env)
      case "get" :: Nil          => handleGetMissingField(env)
      case "--json" :: Nil       => handleJson(env)
      case Nil                   => showUsage(env, CommandResult.error)
      case other                 => handleUnknownArgs(other, env)

  private def configPath(env: CommandEnv): os.Path =
    env.cwd / Constants.Paths.IwDir / Constants.Paths.ConfigFileName

  private def handleGet(field: String, env: CommandEnv): CommandResult =
    env.config.read(configPath(env)) match
      case Left(_) =>
        env.console.err("Error: Configuration not found. Run 'iw init' first.")
        CommandResult.error
      case Right(config) =>
        import upickle.default.*
        import ProjectConfigurationJson.given
        val json = write(config)
        val parsed = ujson.read(json)

        val fieldAliases = Map(
          "trackerType" -> List("tracker", "trackerType"),
          "team" -> List("tracker", "team"),
          "repository" -> List("tracker", "repository"),
          "teamPrefix" -> List("tracker", "teamPrefix"),
          "projectName" -> List("project", "name")
        )
        val optionalFields = Set(
          List("version"),
          List("tracker", "baseUrl"),
          List("tracker", "repository"),
          List("tracker", "teamPrefix")
        )
        val fieldPath =
          fieldAliases.getOrElse(field, field.split("\\.").toList)

        def getValue(
            json: ujson.Value,
            path: List[String]
        ): Try[ujson.Value] =
          path match
            case Nil          => Success(json)
            case head :: tail => Try(json(head)).flatMap(v => getValue(v, tail))

        getValue(parsed, fieldPath) match
          case Success(value) if value.isNull =>
            env.console.err(s"Error: Configuration field '$field' is not set")
            CommandResult.error
          case Success(value) =>
            val outputValue = value match
              case str if str.isInstanceOf[ujson.Str]    => str.str
              case num if num.isInstanceOf[ujson.Num]    => num.num.toString
              case bool if bool.isInstanceOf[ujson.Bool] => bool.bool.toString
              case obj if obj.isInstanceOf[ujson.Obj]    => ujson.write(obj)
              case arr if arr.isInstanceOf[ujson.Arr]    => ujson.write(arr)
              case _                                     => value.toString
            env.console.out(outputValue)
            CommandResult.ok
          case Failure(_) =>
            if optionalFields.contains(fieldPath) then
              env.console.err(s"Error: Configuration field '$field' is not set")
            else env.console.err(s"Error: Unknown configuration field: $field")
            CommandResult.error

  private def handleJson(env: CommandEnv): CommandResult =
    env.config.read(configPath(env)) match
      case Left(_) =>
        env.console.err("Error: Configuration not found. Run 'iw init' first.")
        CommandResult.error
      case Right(config) =>
        import upickle.default.*
        import ProjectConfigurationJson.given
        env.console.out(write(config))
        CommandResult.ok

  private def handleGetMissingField(env: CommandEnv): CommandResult =
    env.console.err("Error: Missing required argument: <field>")
    env.console.out("")
    showUsage(env, CommandResult.error)

  private def handleUnknownArgs(
      args: List[String],
      env: CommandEnv
  ): CommandResult =
    args.headOption match
      case Some(arg) if arg.startsWith("--") =>
        env.console.err(s"Error: Unknown option: $arg")
      case Some(arg) =>
        env.console.err(s"Error: Unknown subcommand: $arg")
      case None => ()
    env.console.out("")
    showUsage(env, CommandResult.error)

  private def showUsage(env: CommandEnv, result: CommandResult): CommandResult =
    val lines = List(
      "iw config - Query project configuration",
      "",
      "Usage:",
      "  iw config get <field>  Get a specific configuration field",
      "  iw config --json       Export full configuration as JSON",
      "",
      "Available fields (use dot-notation or aliases):",
      "",
      "  tracker.trackerType  Issue tracker type (GitHub, GitLab, Linear, YouTrack)",
      "  tracker.team         Team identifier (Linear/YouTrack)",
      "  tracker.repository   Repository in owner/repo format (GitHub/GitLab)",
      "  tracker.teamPrefix   Issue ID prefix (GitHub/GitLab)",
      "  tracker.baseUrl      Base URL for YouTrack/GitLab self-hosted",
      "  project.name         Project name",
      "  version              Tool version",
      "",
      "Short-form field aliases:",
      "  trackerType, team, repository, teamPrefix, projectName"
    )
    lines.foreach(env.console.out)
    result
