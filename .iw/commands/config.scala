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

      // Backward compatibility aliases: flat name -> nested path
      val fieldAliases = Map(
        "trackerType" -> List("tracker", "trackerType"),
        "team" -> List("tracker", "team"),
        "repository" -> List("tracker", "repository"),
        "teamPrefix" -> List("tracker", "teamPrefix"),
        "youtrackBaseUrl" -> List("tracker", "baseUrl"),
        "projectName" -> List("project", "name")
      )

      // Known optional fields (nested paths)
      val optionalFields = Set(
        List("version"),
        List("tracker", "baseUrl"),
        List("tracker", "repository"),
        List("tracker", "teamPrefix")
      )

      // Resolve field path: either use alias or parse dot-notation
      val fieldPath = fieldAliases.getOrElse(field, field.split("\\.").toList)

      // Navigate to the value using the path
      def getValue(json: ujson.Value, path: List[String]): Try[ujson.Value] =
        path match
          case Nil => Success(json)
          case head :: tail => Try(json(head)).flatMap(v => getValue(v, tail))

      getValue(parsed, fieldPath) match
        case Success(value) if value.isNull =>
          Output.error(s"Configuration field '$field' is not set")
          sys.exit(1)
        case Success(value) =>
          // Extract the value based on its type
          val outputValue = value match
            case str if str.isInstanceOf[ujson.Str] => str.str
            case num if num.isInstanceOf[ujson.Num] => num.num.toString
            case bool if bool.isInstanceOf[ujson.Bool] => bool.bool.toString
            case obj if obj.isInstanceOf[ujson.Obj] => ujson.write(obj)
            case arr if arr.isInstanceOf[ujson.Arr] => ujson.write(arr)
            case _ => value.toString

          Output.info(outputValue)
          sys.exit(0)
        case Failure(_) =>
          // Check if it's a known optional field that's not set
          if optionalFields.contains(fieldPath) then
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
  Output.info("Available fields (use dot-notation or aliases):")
  Output.info("")
  Output.info("  tracker.trackerType  Issue tracker type (GitHub, GitLab, Linear, YouTrack)")
  Output.info("  tracker.team         Team identifier (Linear/YouTrack)")
  Output.info("  tracker.repository   Repository in owner/repo format (GitHub/GitLab)")
  Output.info("  tracker.teamPrefix   Issue ID prefix (GitHub/GitLab)")
  Output.info("  tracker.baseUrl      Base URL for YouTrack/GitLab self-hosted")
  Output.info("  project.name         Project name")
  Output.info("  version              Tool version")
  Output.info("")
  Output.info("Aliases for backward compatibility:")
  Output.info("  trackerType, team, repository, teamPrefix, youtrackBaseUrl, projectName")
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
