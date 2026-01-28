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
    case _ =>
      Output.error("Usage: iw config get <field>")
      sys.exit(1)

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
