// PURPOSE: Pure function for building git repository root URLs from project configuration
// PURPOSE: Derives URL from trackerType and repository fields; uses trackerBaseUrl for self-hosted GitLab

package iw.core.model

object RepoUrlBuilder:
  private val AllowedSchemes = Set("https", "http")

  /** Build the git repo root URL from the project's configuration.
    *
    * Returns a URL whenever `repository` is set, regardless of trackerType.
    *   - GitHub: https://github.com/{owner}/{repo}
    *   - GitLab: {trackerBaseUrl|https://gitlab.com}/{owner}/{repo} (handles
    *     nested groups: group/subgroup/project)
    *   - Linear/YouTrack with `repository` set: treated as owner/repo on
    *     GitHub.
    *
    * Returns `None` if `repository` is unset or if the resulting URL does not
    * use an http/https scheme (defence in depth against malformed
    * `trackerBaseUrl` values reaching an `href` attribute).
    *
    * @param config
    *   Project configuration
    * @return
    *   Optional repo URL (None if repository is not set or scheme is unsafe)
    */
  def buildRepoUrl(config: ProjectConfiguration): Option[String] =
    config.repository
      .map { repo =>
        config.trackerType match
          case IssueTrackerType.GitLab =>
            val baseUrl = config.trackerBaseUrl.getOrElse("https://gitlab.com")
            s"${baseUrl.stripSuffix("/")}/$repo"
          case IssueTrackerType.GitHub | IssueTrackerType.Linear |
              IssueTrackerType.YouTrack =>
            s"https://github.com/$repo"
      }
      .filter(hasAllowedScheme)

  private def hasAllowedScheme(url: String): Boolean =
    val schemeEnd = url.indexOf(':')
    schemeEnd > 0 && AllowedSchemes.contains(
      url.substring(0, schemeEnd).toLowerCase
    )
