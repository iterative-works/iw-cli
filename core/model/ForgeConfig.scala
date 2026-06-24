// PURPOSE: Forge-level configuration for HTTP-based forges (currently Forgejo)
// PURPOSE: Carries optional baseUrl and API token resolved in the command layer

package iw.core.model

/** Configuration resolved in the command layer for HTTP-based forge operations.
  *
  * GitHub and GitLab obtain auth and base URLs from their CLI's ambient
  * configuration. Forgejo requires explicit `baseUrl` and API token. This type
  * is passed alongside `gitlabHost` to the four TrackerOps forge methods;
  * GitHub and GitLab arms ignore it.
  *
  * @param baseUrl
  *   Base URL of the forge instance (e.g., "https://codeberg.org")
  * @param token
  *   API token for authentication
  */
case class ForgeConfig(baseUrl: Option[String], token: Option[ApiToken])

object ForgeConfig:
  /** An empty ForgeConfig for GitHub/GitLab paths that do not use it. */
  val empty: ForgeConfig = ForgeConfig(None, None)

  /** Error messages emitted when required Forgejo fields are absent. */
  val missingBaseUrlError: String =
    "Forgejo base URL not configured. Add 'baseUrl' to tracker section in .iw/config.conf"
  val missingTokenError: String =
    s"${Constants.EnvVars.ForgejoApiToken} environment variable is not set"
