// PURPOSE: Query and export project configuration values
// PURPOSE: Provides programmatic access to .iw/config.conf for workflows

import iw.core.model.*
import iw.core.adapters.*
import iw.core.output.Output
import iw.core.model.ProjectConfigurationJson.given
import scala.util.{Try, Success, Failure}

@main def config(args: String*): Unit =
  args.toList match
    case "get" :: field :: Nil => handleGet(field)
    case "get" :: Nil => handleGetMissingField()
    case "--json" :: Nil => handleJson()
    case Nil => showUsage()
    case other => handleUnknownArgs(other)

def handleGet(field: String): Unit =
  val configPath = os.Path(System.getProperty(Constants.SystemProps.UserDir)) / Constants.Paths.IwDir / Constants.Paths.ConfigFileName

  ConfigFileRepository.read(configPath) match
    case None =>
      Output.error("Configuration not found. Run 'iw init' first.")
      sys.exit(1)
    case Some(config) =>
      import upickle.default.*
      val json = write(config)
      val parsed = ujson.read(json)

      // Known optional fields in ProjectConfiguration
      val optionalFields = Set("version", "youtrackBaseUrl", "repository", "teamPrefix")

      Try(parsed(field)) match
        case Success(value) if value.isNull =>
          Output.error(s"Configuration field '$field' is not set")
          sys.exit(1)
        case Success(value) =>
          // Extract the value based on its type
          val outputValue = value match
            case str if str.isInstanceOf[ujson.Str] => str.str
            case num if num.isInstanceOf[ujson.Num] => num.num.toString
            case bool if bool.isInstanceOf[ujson.Bool] => bool.bool.toString
            case _ => value.toString

          Output.info(outputValue)
          sys.exit(0)
        case Failure(_) =>
          // Check if it's a known optional field that's not set
          if optionalFields.contains(field) then
            Output.error(s"Configuration field '$field' is not set")
          else
            Output.error(s"Unknown configuration field: $field")
          sys.exit(1)

def handleJson(): Unit =
  val configPath = os.Path(System.getProperty(Constants.SystemProps.UserDir)) / Constants.Paths.IwDir / Constants.Paths.ConfigFileName

  ConfigFileRepository.read(configPath) match
    case None =>
      Output.error("Configuration not found. Run 'iw init' first.")
      sys.exit(1)
    case Some(config) =>
      import upickle.default.*
      val json = write(config)
      Output.info(json)
      sys.exit(0)

def showUsage(): Unit =
  Output.info("iw config - Query project configuration")
  Output.info("")
  Output.info("Usage:")
  Output.info("  iw config get <field>  Get a specific configuration field")
  Output.info("  iw config --json       Export full configuration as JSON")
  Output.info("")
  Output.info("Available fields:")
  Output.info("  trackerType     Issue tracker type (GitHub, GitLab, Linear, YouTrack)")
  Output.info("  team            Team identifier (Linear/YouTrack)")
  Output.info("  projectName     Project name")
  Output.info("  repository      Repository in owner/repo format (GitHub/GitLab)")
  Output.info("  teamPrefix      Issue ID prefix (GitHub/GitLab)")
  Output.info("  version         Tool version")
  Output.info("  youtrackBaseUrl Base URL for YouTrack/GitLab self-hosted")
  sys.exit(1)

def handleGetMissingField(): Unit =
  Output.error("Missing required argument: <field>")
  Output.info("")
  showUsage()

def handleUnknownArgs(args: List[String]): Unit =
  args.headOption match
    case Some(arg) if arg.startsWith("--") =>
      Output.error(s"Unknown option: $arg")
    case Some(arg) =>
      Output.error(s"Unknown subcommand: $arg")
    case None => ()
  Output.info("")
  showUsage()
