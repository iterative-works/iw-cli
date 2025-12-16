// PURPOSE: Configuration domain model for issue tracker integration
// PURPOSE: Defines GitRemote, IssueTrackerType, and ProjectConfiguration value objects

package iw.core

import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}

case class GitRemote(url: String):
  def host: Either[String, String] =
    // Extract host from either SSH (git@host:path) or HTTPS (https://host/path) format
    def validateHost(extracted: String): Either[String, String] =
      if extracted.isEmpty then
        Left(s"Unsupported git URL format: $url")
      else
        Right(extracted)

    if url.startsWith("git@") then
      // SSH format: git@github.com:user/repo.git
      val afterAt = url.drop(4) // remove "git@"
      if !afterAt.contains(':') then
        Left(s"Unsupported git URL format: $url")
      else
        validateHost(afterAt.takeWhile(_ != ':'))
    else if url.startsWith("https://") || url.startsWith("http://") then
      // HTTPS format: https://github.com/user/repo.git
      val withoutProtocol = url.dropWhile(_ != '/').drop(2) // remove protocol and //
      validateHost(withoutProtocol.takeWhile(_ != '/'))
    else
      Left(s"Unsupported git URL format: $url")

enum IssueTrackerType:
  case Linear, YouTrack

case class ProjectConfiguration(
  trackerType: IssueTrackerType,
  team: String,
  projectName: String,
  version: Option[String] = Some("latest"),
  youtrackBaseUrl: Option[String] = None
)

object TrackerDetector:
  def suggestTracker(remote: GitRemote): Option[IssueTrackerType] =
    remote.host match
      case Right("github.com") => Some(IssueTrackerType.Linear)
      case Right("gitlab.e-bs.cz") => Some(IssueTrackerType.YouTrack)
      case _ => None

object ConfigSerializer:
  def toHocon(config: ProjectConfiguration): String =
    val trackerTypeStr = config.trackerType match
      case IssueTrackerType.Linear => Constants.TrackerTypeValues.Linear
      case IssueTrackerType.YouTrack => Constants.TrackerTypeValues.YouTrack

    val versionLine = config.version.map(v => s"\nversion = $v").getOrElse("")
    val youtrackUrlLine = config.youtrackBaseUrl.map(url => s"""\n  baseUrl = "$url"""").getOrElse("")

    s"""tracker {
       |  type = $trackerTypeStr
       |  team = ${config.team}$youtrackUrlLine
       |}
       |
       |project {
       |  name = ${config.projectName}
       |}$versionLine
       |""".stripMargin

  def fromHocon(hocon: String): Either[String, ProjectConfiguration] =
    try
      val config = ConfigFactory.parseString(hocon)

      val trackerTypeStr = config.getString(Constants.ConfigKeys.TrackerType)
      val trackerType = trackerTypeStr match
        case Constants.TrackerTypeValues.Linear => IssueTrackerType.Linear
        case Constants.TrackerTypeValues.YouTrack => IssueTrackerType.YouTrack
        case other => return Left(s"Unknown tracker type: $other")

      val team = config.getString(Constants.ConfigKeys.TrackerTeam)
      val projectName = config.getString(Constants.ConfigKeys.ProjectName)

      // Read version, default to "latest" if not present
      val version = if config.hasPath(Constants.ConfigKeys.Version) then
        Some(config.getString(Constants.ConfigKeys.Version))
      else
        Some("latest")

      // Read YouTrack base URL if present
      val youtrackBaseUrl = if config.hasPath(Constants.ConfigKeys.TrackerBaseUrl) then
        Some(config.getString(Constants.ConfigKeys.TrackerBaseUrl))
      else
        None

      Right(ProjectConfiguration(trackerType, team, projectName, version, youtrackBaseUrl))
    catch
      case e: Exception => Left(s"Failed to parse config: ${e.getMessage}")
