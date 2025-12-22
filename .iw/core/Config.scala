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

  def repositoryOwnerAndName: Either[String, String] =
    // First verify this is a GitHub URL
    host match
      case Left(err) => Left(err)
      case Right(h) if h != "github.com" => Left("Not a GitHub URL")
      case Right(_) =>
        // Extract path component (after host)
        val path = if url.startsWith("git@") then
          // SSH format: git@github.com:owner/repo.git
          val afterColon = url.dropWhile(_ != ':').drop(1)
          afterColon.stripSuffix(".git")
        else
          // HTTPS format: https://github.com/owner/repo.git
          val afterHost = url.dropWhile(_ != '/').drop(2).dropWhile(_ != '/').drop(1)
          afterHost.stripSuffix(".git")

        // Validate format: should be owner/repo
        // Use split limit -1 to preserve trailing empty strings
        if path.count(_ == '/') != 1 then
          Left("Invalid repository format: expected owner/repo")
        else if path.split("/", -1).exists(_.isEmpty) then
          Left("Invalid repository format: expected owner/repo")
        else
          Right(path)

enum IssueTrackerType:
  case Linear, YouTrack, GitHub

case class ProjectConfiguration(
  trackerType: IssueTrackerType,
  team: String,
  projectName: String,
  version: Option[String] = Some("latest"),
  youtrackBaseUrl: Option[String] = None,
  repository: Option[String] = None
)

object TrackerDetector:
  def suggestTracker(remote: GitRemote): Option[IssueTrackerType] =
    remote.host match
      case Right("github.com") => Some(IssueTrackerType.GitHub)
      case Right("gitlab.e-bs.cz") => Some(IssueTrackerType.YouTrack)
      case _ => None

object ConfigSerializer:
  def toHocon(config: ProjectConfiguration): String =
    val trackerTypeStr = config.trackerType match
      case IssueTrackerType.Linear => Constants.TrackerTypeValues.Linear
      case IssueTrackerType.YouTrack => Constants.TrackerTypeValues.YouTrack
      case IssueTrackerType.GitHub => Constants.TrackerTypeValues.GitHub

    val versionLine = config.version.map(v => s"\nversion = $v").getOrElse("")
    val youtrackUrlLine = config.youtrackBaseUrl.map(url => s"""\n  baseUrl = "$url"""").getOrElse("")

    // For GitHub, use repository instead of team
    val trackerDetails = config.trackerType match
      case IssueTrackerType.GitHub =>
        config.repository.map(repo => s"""repository = "$repo"""").getOrElse("")
      case _ =>
        s"team = ${config.team}$youtrackUrlLine"

    s"""tracker {
       |  type = $trackerTypeStr
       |  $trackerDetails
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
        case Constants.TrackerTypeValues.GitHub => IssueTrackerType.GitHub
        case other => return Left(s"Unknown tracker type: $other")

      // For GitHub, read repository; for others, read team
      val (team, repository) = trackerType match
        case IssueTrackerType.GitHub =>
          if !config.hasPath(Constants.ConfigKeys.TrackerRepository) then
            return Left("repository required for GitHub tracker")
          val repo = config.getString(Constants.ConfigKeys.TrackerRepository)
          // Validate repository format: owner/repo
          if !repo.contains('/') || repo.count(_ == '/') != 1 || repo.split('/').exists(_.isEmpty) then
            return Left("repository must be in owner/repo format")
          ("", Some(repo))
        case _ =>
          val t = config.getString(Constants.ConfigKeys.TrackerTeam)
          (t, None)

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

      Right(ProjectConfiguration(trackerType, team, projectName, version, youtrackBaseUrl, repository))
    catch
      case e: Exception => Left(s"Failed to parse config: ${e.getMessage}")
