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
  version: Option[String] = Some("latest")
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
      case IssueTrackerType.Linear => "linear"
      case IssueTrackerType.YouTrack => "youtrack"

    val versionLine = config.version.map(v => s"\nversion = $v").getOrElse("")

    s"""tracker {
       |  type = $trackerTypeStr
       |  team = ${config.team}
       |}
       |
       |project {
       |  name = ${config.projectName}
       |}$versionLine
       |""".stripMargin

  def fromHocon(hocon: String): Either[String, ProjectConfiguration] =
    try
      val config = ConfigFactory.parseString(hocon)

      val trackerTypeStr = config.getString("tracker.type")
      val trackerType = trackerTypeStr match
        case "linear" => IssueTrackerType.Linear
        case "youtrack" => IssueTrackerType.YouTrack
        case other => return Left(s"Unknown tracker type: $other")

      val team = config.getString("tracker.team")
      val projectName = config.getString("project.name")

      // Read version, default to "latest" if not present
      val version = if config.hasPath("version") then
        Some(config.getString("version"))
      else
        Some("latest")

      Right(ProjectConfiguration(trackerType, team, projectName, version))
    catch
      case e: Exception => Left(s"Failed to parse config: ${e.getMessage}")
