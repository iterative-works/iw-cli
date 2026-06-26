// PURPOSE: Resolves forge configuration from project config and environment variables
// PURPOSE: Returns Either[String, ForgeConfig] so missing-field errors surface early in the command pipeline

package iw.core.commands

import iw.core.model.{
  ApiToken,
  Constants,
  ForgeConfig,
  ForgeType,
  ProjectConfiguration
}

object ForgeConfigResolver:

  /** Resolve the [[ForgeConfig]] for the given forge type.
    *
    * For Forgejo: reads `baseUrl` from `config.trackerBaseUrl` and the API
    * token from the `FORGEJO_API_TOKEN` environment variable. Returns
    * `Left(error)` if either is absent so that callers can fail early.
    *
    * For GitHub and GitLab: those forges obtain auth from their ambient CLI
    * configuration, so `ForgeConfig.empty` is always sufficient and this method
    * returns `Right(ForgeConfig.empty)`.
    *
    * @param forgeType
    *   Which forge is in use
    * @param config
    *   Project configuration (source of `trackerBaseUrl`)
    * @param env
    *   Command environment (source of env-var lookup)
    * @return
    *   Right(ForgeConfig) on success, Left(error message) when a required
    *   Forgejo field is missing
    */
  def resolve(
      forgeType: ForgeType,
      config: ProjectConfiguration,
      env: CommandEnv
  ): Either[String, ForgeConfig] =
    forgeType match
      case ForgeType.Forgejo =>
        val baseUrl = config.trackerBaseUrl
        val token = env.envVars
          .get(Constants.EnvVars.ForgejoApiToken)
          .flatMap(ApiToken.apply)
        for
          _ <- baseUrl.toRight(ForgeConfig.missingBaseUrlError)
          _ <- token.toRight(ForgeConfig.missingTokenError)
        yield ForgeConfig(baseUrl, token)
      case _ => Right(ForgeConfig.empty)
