// PURPOSE: Repository for server configuration file persistence
// PURPOSE: Handles reading and writing ServerConfig to/from JSON

package iw.core

import upickle.default.*
import java.nio.file.{Files, Paths, NoSuchFileException}
import scala.util.{Try, Success, Failure}

object ServerConfigRepository:

  /** Read config from JSON file */
  def read(path: String): Either[String, ServerConfig] =
    Try {
      val jsonPath = Paths.get(path)
      if !Files.exists(jsonPath) then
        throw new NoSuchFileException(s"Config file not found: $path")

      val json = os.read(os.Path(path))
      val config = upickle.default.read[ServerConfig](json)

      // Validate the port after deserialization
      ServerConfig.validate(config.port) match
        case Right(_) => config
        case Left(err) => throw new IllegalArgumentException(err)
    } match
      case Success(config) => Right(config)
      case Failure(e: NoSuchFileException) => Left(s"Config file does not exist: $path")
      case Failure(e: IllegalArgumentException) => Left(e.getMessage)
      case Failure(e) => Left(s"Failed to parse config: ${e.getMessage}")

  /** Write config to JSON file */
  def write(config: ServerConfig, path: String): Either[String, Unit] =
    Try {
      // Validate before writing
      ServerConfig.validate(config.port) match
        case Left(err) => throw new IllegalArgumentException(err)
        case Right(_) => ()

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
