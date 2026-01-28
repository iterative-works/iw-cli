// PURPOSE: Configuration domain model for issue tracker integration
// PURPOSE: Defines GitRemote, IssueTrackerType, and ProjectConfiguration value objects

package iw.core.model

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
      // HTTPS format: https://github.com/user/repo.git or https://username@github.com/user/repo.git
      val withoutProtocol = url.dropWhile(_ != '/').drop(2) // remove protocol and //
      // Handle username prefix (username@host)
      val hostPart = withoutProtocol.takeWhile(_ != '/')
      val host = if hostPart.contains('@') then
        hostPart.dropWhile(_ != '@').drop(1) // remove username@
      else
        hostPart
      validateHost(host)
    else
      Left(s"Unsupported git URL format: $url")

  def repositoryOwnerAndName: Either[String, String] =
    // First verify this is a GitHub URL
    host match
      case Left(err) => Left(err)
      case Right(h) if h != "github.com" => Left("Not a GitHub URL")
      case Right(_) =>
        // Extract path component (after host)
        val rawPath = if url.startsWith("git@") then
          // SSH format: git@github.com:owner/repo.git
          val afterColon = url.dropWhile(_ != ':').drop(1)
          afterColon
        else
          // HTTPS format: https://github.com/owner/repo.git or https://username@github.com/owner/repo.git
          val afterProtocol = url.dropWhile(_ != '/').drop(2) // remove protocol and //
          // Skip username@ if present
          val afterUsername = if afterProtocol.contains('@') then
            afterProtocol.dropWhile(_ != '@').drop(1)
          else
            afterProtocol
          // Extract path after host
          afterUsername.dropWhile(_ != '/').drop(1)

        // Clean up path: remove trailing slash and .git suffix
        val path = rawPath.stripSuffix("/").stripSuffix(".git").stripSuffix("/")

        // Validate format: should be owner/repo
        // Use split limit -1 to preserve trailing empty strings
        if path.count(_ == '/') != 1 then
          Left("Invalid repository format: expected owner/repo")
        else if path.split("/", -1).exists(_.isEmpty) then
          Left("Invalid repository format: expected owner/repo")
        else
          Right(path)

  def extractGitLabRepository: Either[String, String] =
    // First verify this is a GitLab URL
    host match
      case Left(err) => Left(err)
      case Right(h) if h != "gitlab.com" && !h.contains("gitlab") =>
        Left("Not a GitLab URL")
      case Right(_) =>
        // Extract path component (after host)
        val rawPath = if url.startsWith("git@") then
          // SSH format: git@gitlab.com:owner/repo.git or git@gitlab.com:group/subgroup/project.git
          val afterColon = url.dropWhile(_ != ':').drop(1)
          afterColon
        else
          // HTTPS format: https://gitlab.com/owner/repo.git or https://gitlab.com/group/subgroup/project.git
          val afterProtocol = url.dropWhile(_ != '/').drop(2) // remove protocol and //
          // Skip username@ if present
          val afterUsername = if afterProtocol.contains('@') then
            afterProtocol.dropWhile(_ != '@').drop(1)
          else
            afterProtocol
          // Extract path after host
          afterUsername.dropWhile(_ != '/').drop(1)

        // Clean up path: remove trailing slash and .git suffix
        val path = rawPath.stripSuffix("/").stripSuffix(".git").stripSuffix("/")

        // Validate format: should have at least one slash (owner/repo or group/subgroup/project)
        if !path.contains('/') then
          Left("Invalid repository format: expected at least owner/repo")
        else if path.split("/", -1).exists(_.isEmpty) then
          Left("Invalid repository format: path components cannot be empty")
        else
          Right(path)

enum IssueTrackerType:
  case Linear, YouTrack, GitHub, GitLab

case class TrackerConfig(
  trackerType: IssueTrackerType,
  team: String = "",
  repository: Option[String] = None,
  teamPrefix: Option[String] = None,
  baseUrl: Option[String] = None
)

case class ProjectConfig(
  name: String
)

case class ProjectConfiguration(
  tracker: TrackerConfig,
  project: ProjectConfig,
  version: Option[String] = Some("latest")
):
  // Backward compatibility accessors
  def trackerType: IssueTrackerType = tracker.trackerType
  def team: String = tracker.team
  def projectName: String = project.name
  def repository: Option[String] = tracker.repository
  def teamPrefix: Option[String] = tracker.teamPrefix
  def youtrackBaseUrl: Option[String] = tracker.baseUrl

object ProjectConfiguration:
  // Factory method for flat parameter style (used by tests and legacy code)
  def create(
    trackerType: IssueTrackerType,
    team: String = "",
    projectName: String,
    version: Option[String] = Some("latest"),
    youtrackBaseUrl: Option[String] = None,
    repository: Option[String] = None,
    teamPrefix: Option[String] = None
  ): ProjectConfiguration =
    ProjectConfiguration(
      tracker = TrackerConfig(trackerType, team, repository, teamPrefix, youtrackBaseUrl),
      project = ProjectConfig(projectName),
      version = version
    )

object TeamPrefixValidator:
  private val ValidPattern = """^[A-Z]{2,10}$""".r

  def validate(prefix: String): Either[String, String] =
    prefix match
      case ValidPattern() => Right(prefix)
      case _ if prefix.length < 2 || prefix.length > 10 =>
        Left(s"Team prefix must be 2-10 characters, got ${prefix.length}")
      case _ =>
        Left("Team prefix must contain uppercase letters only (A-Z)")

  def suggestFromRepository(repository: String): String =
    // Extract repo name from owner/repo format
    val repoName = repository.split("/").lastOption.getOrElse(repository)
    // Remove hyphens and convert to uppercase
    val cleaned = repoName.replace("-", "").toUpperCase
    // Truncate to 10 characters
    cleaned.take(10)

object TrackerDetector:
  def suggestTracker(remote: GitRemote): Option[IssueTrackerType] =
    remote.host match
      case Right("github.com") => Some(IssueTrackerType.GitHub)
      case Right("gitlab.e-bs.cz") => Some(IssueTrackerType.YouTrack)
      case Right("gitlab.com") => Some(IssueTrackerType.GitLab)
      case Right(host) if host.contains("gitlab") => Some(IssueTrackerType.GitLab)
      case _ => None

object ConfigSerializer:
  def toHocon(config: ProjectConfiguration): String =
    val trackerTypeStr = config.trackerType match
      case IssueTrackerType.Linear => Constants.TrackerTypeValues.Linear
      case IssueTrackerType.YouTrack => Constants.TrackerTypeValues.YouTrack
      case IssueTrackerType.GitHub => Constants.TrackerTypeValues.GitHub
      case IssueTrackerType.GitLab => Constants.TrackerTypeValues.GitLab

    val versionLine = config.version.map(v => s"\nversion = $v").getOrElse("")
    val baseUrlLine = config.youtrackBaseUrl.map(url => s"""\n  baseUrl = "$url"""").getOrElse("")

    // For GitHub and GitLab, use repository and teamPrefix instead of team
    val trackerDetails = config.trackerType match
      case IssueTrackerType.GitHub =>
        val repoLine = config.repository.map(repo => s"""repository = "$repo"""").getOrElse("")
        val prefixLine = config.teamPrefix.map(p => s"""\n  teamPrefix = "$p"""").getOrElse("")
        s"$repoLine$prefixLine"
      case IssueTrackerType.GitLab =>
        val repoLine = config.repository.map(repo => s"""repository = "$repo"""").getOrElse("")
        val prefixLine = config.teamPrefix.map(p => s"""\n  teamPrefix = "$p"""").getOrElse("")
        s"$repoLine$prefixLine$baseUrlLine"
      case _ =>
        s"team = ${config.team}$baseUrlLine"

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
        case Constants.TrackerTypeValues.GitLab => IssueTrackerType.GitLab
        case other => return Left(s"Unknown tracker type: $other")

      // For GitHub and GitLab, read repository and teamPrefix; for others, read team
      val (team, repository, teamPrefix) = trackerType match
        case IssueTrackerType.GitHub | IssueTrackerType.GitLab =>
          val trackerName = if trackerType == IssueTrackerType.GitHub then "GitHub" else "GitLab"
          if !config.hasPath(Constants.ConfigKeys.TrackerRepository) then
            return Left(s"repository required for $trackerName tracker")
          val repo = config.getString(Constants.ConfigKeys.TrackerRepository)
          // Validate repository format: owner/repo or owner/group/repo for GitLab
          if !repo.contains('/') || repo.split('/').exists(_.isEmpty) then
            return Left("repository must be in owner/repo format")

          // Require and validate teamPrefix for GitHub and GitLab
          if !config.hasPath(Constants.ConfigKeys.TrackerTeamPrefix) then
            return Left(s"teamPrefix required for $trackerName tracker")
          val prefix = config.getString(Constants.ConfigKeys.TrackerTeamPrefix)
          TeamPrefixValidator.validate(prefix) match
            case Left(err) => return Left(err)
            case Right(validPrefix) => ("", Some(repo), Some(validPrefix))
        case _ =>
          val t = config.getString(Constants.ConfigKeys.TrackerTeam)
          (t, None, None)

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

      Right(ProjectConfiguration(
        tracker = TrackerConfig(trackerType, team, repository, teamPrefix, youtrackBaseUrl),
        project = ProjectConfig(projectName),
        version = version
      ))
    catch
      case e: Exception => Left(s"Failed to parse config: ${e.getMessage}")

object ProjectConfigurationJson:
  import upickle.default.*

  given ReadWriter[IssueTrackerType] = readwriter[String].bimap(
    _.toString,
    s => IssueTrackerType.valueOf(s)
  )

  given ReadWriter[TrackerConfig] = macroRW
  given ReadWriter[ProjectConfig] = macroRW
  given ReadWriter[ProjectConfiguration] = macroRW
