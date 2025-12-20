// PURPOSE: Server configuration domain model for port settings
// PURPOSE: Validates port range and provides JSON serialization

package iw.core

import upickle.default.*

case class ServerConfig(port: Int) derives ReadWriter

object ServerConfig:
  val DefaultPort: Int = 9876
  val MinPort: Int = 1024
  val MaxPort: Int = 65535

  /** Validate port is in allowed range (1024-65535) */
  def validate(port: Int): Either[String, Int] =
    if port < MinPort || port > MaxPort then
      Left(s"Port must be between $MinPort and $MaxPort, got: $port")
    else
      Right(port)

  /** Create config with validation */
  def create(port: Int): Either[String, ServerConfig] =
    validate(port).map(ServerConfig(_))

  /** Create default config with standard port */
  def default: ServerConfig = ServerConfig(DefaultPort)
