// PURPOSE: Server configuration domain model for port and host settings
// PURPOSE: Validates port range and host addresses, provides JSON serialization

package iw.core.model

import upickle.default.*

case class ServerConfig(port: Int, hosts: Seq[String] = Seq("localhost")) derives ReadWriter

case class SecurityAnalysis(
  exposedHosts: Seq[String],
  bindsToAll: Boolean,
  hasWarning: Boolean
)

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

  /** Validate host address */
  def validateHost(host: String): Either[String, String] =
    if host.isEmpty then
      Left("Invalid host: empty string")
    else if host == "localhost" || host == "127.0.0.1" || host == "::1" then
      Right(host)
    else if host == "0.0.0.0" || host == "::" then
      Right(host)
    else if isValidIPv4(host) then
      Right(host)
    else if isValidIPv6(host) then
      Right(host)
    else
      Left(s"Invalid host: $host")

  /** Validate multiple hosts */
  def validateHosts(hosts: Seq[String]): Either[String, Seq[String]] =
    if hosts.isEmpty then
      Left("Configuration must have at least one host")
    else
      hosts.foldLeft[Either[String, Seq[String]]](Right(Seq.empty)) { (acc, host) =>
        acc.flatMap { validHosts =>
          validateHost(host).map(validHost => validHosts :+ validHost)
        }
      }

  /** Basic IPv4 validation */
  private def isValidIPv4(host: String): Boolean =
    val ipv4Pattern = """^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""".r
    ipv4Pattern.matches(host) && {
      val parts = host.split('.')
      parts.length == 4 && parts.forall { part =>
        val num = part.toIntOption
        num.exists(n => n >= 0 && n <= 255)
      }
    }

  /** Basic IPv6 validation */
  private def isValidIPv6(host: String): Boolean =
    host.contains(':') && host.forall(c => c.isDigit || "abcdefABCDEF:".contains(c))

  /** Create config with validation */
  def create(port: Int): Either[String, ServerConfig] =
    validate(port).map(ServerConfig(_))

  /** Create config with validation including hosts */
  def create(port: Int, hosts: Seq[String]): Either[String, ServerConfig] =
    for {
      validPort <- validate(port)
      validHosts <- validateHosts(hosts)
    } yield ServerConfig(validPort, validHosts)

  /** Create default config with standard port */
  def default: ServerConfig = ServerConfig(DefaultPort)

  /** Check if host is a localhost variant (no security warning needed) */
  def isLocalhostVariant(host: String): Boolean =
    host == "localhost" || host == "::1" || isIPv4Loopback(host)

  /** Check if IPv4 address is in loopback range (127.x.x.x) */
  private def isIPv4Loopback(host: String): Boolean =
    val ipv4Pattern = """^127\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""".r
    ipv4Pattern.matches(host) && {
      val parts = host.split('.').drop(1) // skip "127"
      parts.forall { part =>
        val num = part.toIntOption
        num.exists(n => n >= 0 && n <= 255)
      }
    }

  /** Analyze hosts configuration for security implications */
  def analyzeHostsSecurity(hosts: Seq[String]): SecurityAnalysis =
    val exposedHosts = hosts.filterNot(isLocalhostVariant)
    val bindsToAll = exposedHosts.exists(h => h == "0.0.0.0" || h == "::")
    val hasWarning = exposedHosts.nonEmpty
    SecurityAnalysis(exposedHosts, bindsToAll, hasWarning)
