// PURPOSE: Double-gated dev mode configuration — requires both --dev flag and VITE_DEV_URL
// PURPOSE: Validates that the Vite dev URL uses http scheme and a loopback host

package iw.dashboard

enum DevModeConfig:
  case Off
  case On(viteDevUrl: String)

object DevModeConfig:
  /** Resolve dev mode configuration from flag and environment variable.
    *
    * Both the --dev flag AND a non-empty VITE_DEV_URL are required to activate
    * dev routing. Flag-alone yields Off (with a warning at the call site).
    * Env-alone (flag false) always yields Off.
    *
    * @param devFlag
    *   Whether --dev was passed on the command line
    * @param viteDevUrlEnv
    *   Value of the VITE_DEV_URL environment variable, if set
    * @return
    *   Right(Off) when dev mode is inactive; Right(On(url)) when active and
    *   valid; Left(errorMessage) when flag is set but URL fails validation
    */
  def resolve(
      devFlag: Boolean,
      viteDevUrlEnv: Option[String]
  ): Either[String, DevModeConfig] =
    if !devFlag then Right(Off)
    else
      viteDevUrlEnv.filter(_.nonEmpty) match
        case None      => Right(Off)
        case Some(raw) => validate(raw).map(On.apply)

  private def validate(raw: String): Either[String, String] =
    scala.util.Try(java.net.URI.create(raw)) match
      case scala.util.Failure(e) =>
        Left(s"VITE_DEV_URL is not a valid URI: ${e.getMessage}")
      case scala.util.Success(uri) =>
        val scheme = Option(uri.getScheme).getOrElse("")
        val host = Option(uri.getHost).getOrElse("")
        if scheme != "http" then
          Left(s"VITE_DEV_URL scheme must be http (got: $scheme)")
        else if !loopbackHosts.contains(host) then
          Left(s"VITE_DEV_URL host must be loopback (got: $host)")
        else Right(raw)

  // IPv6 loopback intentionally excluded per DM-IPV6 resolution (2026-04-23).
  private val loopbackHosts = Set("localhost", "127.0.0.1")
