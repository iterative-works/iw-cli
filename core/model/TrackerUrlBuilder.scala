// PURPOSE: Pure function for building issue tracker URLs from project configuration
// PURPOSE: Centralizes URL construction logic for all supported tracker types

package iw.core.model

object TrackerUrlBuilder:
  /** Build the issue tracker URL for a project based on its configuration.
    *
    * @param config
    *   Project configuration
    * @return
    *   Optional tracker URL (None if not enough info to build one)
    */
  def buildTrackerUrl(config: ProjectConfiguration): Option[String] =
    config.trackerType match
      case IssueTrackerType.GitHub =>
        config.repository.map(repo => s"https://github.com/$repo/issues")
      case IssueTrackerType.Linear =>
        Some(s"https://linear.app/${config.team.toLowerCase}")
      case IssueTrackerType.YouTrack =>
        config.youtrackBaseUrl.map(baseUrl =>
          s"${baseUrl.stripSuffix("/")}/issues/${config.team}"
        )
      case IssueTrackerType.GitLab =>
        config.repository.map(repo =>
          val baseUrl = config.youtrackBaseUrl.getOrElse("https://gitlab.com")
          s"${baseUrl.stripSuffix("/")}/$repo/-/issues"
        )
