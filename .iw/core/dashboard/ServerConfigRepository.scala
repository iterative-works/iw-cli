// PURPOSE: Repository for server configuration file persistence
// PURPOSE: Handles reading and writing ServerConfig to/from JSON

package iw.core.dashboard

import iw.core.model.ServerConfig
import upickle.default.*
import java.nio.file.{Files, Paths, NoSuchFileException}
import scala.util.{Try, Success, Failure}

object ServerConfigRepository:

  /** Read config from JSON file */
  def read(path: String): Either[String, ServerConfig] =
    val jsonPath = Paths.get(path)
    if !Files.exists(jsonPath) then
      Left(s"Config file does not exist: $path")
    else
      Try {
        val json = os.read(os.Path(path))
        upickle.default.read[ServerConfig](json)
      } match
        case Failure(e) => Left(s"Failed to parse config: ${e.getMessage}")
        case Success(config) =>
          ServerConfig.validate(config.port) match
            case Left(err) => Left(err)
            case Right(_) =>
              ServerConfig.validateHosts(config.hosts) match
                case Left(err) => Left(err)
                case Right(_) => Right(config)

  /** Write config to JSON file */
  def write(config: ServerConfig, path: String): Either[String, Unit] =
    ServerConfig.validate(config.port) match
      case Left(err) => Left(s"Failed to write config: $err")
      case Right(_) =>
        ServerConfig.validateHosts(config.hosts) match
          case Left(err) => Left(s"Failed to write config: $err")
          case Right(_) =>
            Try {
              val osPath = os.Path(path)
              // Create parent directories if they don't exist
              os.makeDir.all(osPath / os.up)

              val json = upickle.default.write(config, indent = 2)
              os.write.over(osPath, json)
            } match
              case Success(_) => Right(())
              case Failure(e) => Left(s"Failed to write config: ${e.getMessage}")

  /** Get existing config or create default if missing */
  def getOrCreateDefault(path: String): Either[String, ServerConfig] =
    read(path) match
      case Right(config) => Right(config)
      case Left(_) =>
        // File doesn't exist, create default
        val defaultConfig = ServerConfig.default
        write(defaultConfig, path) match
          case Right(_) => Right(defaultConfig)
          case Left(err) => Left(err)
