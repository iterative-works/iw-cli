// PURPOSE: Fetch and display issue information from the tracker
// USAGE: iw issue [issue-id]
// ARGS:
//   [issue-id]: Optional issue identifier to fetch. If not provided, infers from current branch
// EXAMPLE: iw issue IWLE-123

import iw.core.*
import iw.core.infrastructure.ServerClient

@main def issue(args: String*): Unit =
  val result = for {
    config <- loadConfig()
    issueId <- getIssueId(args, config)
    issue <- fetchIssue(issueId, config)
  } yield (issue, issueId, config)

  result match
    case Right((issue, issueId, config)) =>
      val formatted = IssueFormatter.format(issue)
      println(formatted)

      // Update dashboard timestamp (best-effort)
      val currentDir = os.Path(System.getProperty(Constants.SystemProps.UserDir))
      val worktreePath = WorktreePath(config.projectName, issueId)
      val targetPath = worktreePath.resolve(currentDir)

      ServerClient.updateLastSeen(
        issueId.value,
        targetPath.toString,
        config.trackerType.toString,
        issueId.team
      ) match
        case Left(_) => () // Ignore errors silently at exit
        case Right(_) => ()

    case Left(error) =>
      Output.error(error)
      sys.exit(1)

def getIssueId(args: Seq[String], config: ProjectConfiguration): Either[String, IssueId] =
  if args.isEmpty then
    // Infer from current branch
    val currentDir = os.Path(System.getProperty(Constants.SystemProps.UserDir))
    for {
      branch <- GitAdapter.getCurrentBranch(currentDir)
      issueId <- IssueId.fromBranch(branch)
    } yield issueId
  else
    // Parse explicit issue ID with team prefix from config (for GitHub/GitLab trackers)
    val teamPrefix = config.trackerType match
      case IssueTrackerType.GitHub | IssueTrackerType.GitLab =>
        config.teamPrefix
      case _ => None
    IssueId.parse(args.head, teamPrefix)

def loadConfig(): Either[String, ProjectConfiguration] =
  val configPath = os.Path(System.getProperty(Constants.SystemProps.UserDir)) / Constants.Paths.IwDir / "config.conf"
  ConfigFileRepository.read(configPath) match
    case Some(config) => Right(config)
    case None => Left("Configuration file not found. Run 'iw init' first.")

def fetchIssue(issueId: IssueId, config: ProjectConfiguration): Either[String, Issue] =
  config.trackerType match
    case IssueTrackerType.Linear =>
      ApiToken.fromEnv(Constants.EnvVars.LinearApiToken) match
        case None =>
          Left(s"${Constants.EnvVars.LinearApiToken} environment variable is not set")
        case Some(token) =>
          LinearClient.fetchIssue(issueId, token)

    case IssueTrackerType.YouTrack =>
      ApiToken.fromEnv(Constants.EnvVars.YouTrackApiToken) match
        case None =>
          Left(s"${Constants.EnvVars.YouTrackApiToken} environment variable is not set")
        case Some(token) =>
          config.youtrackBaseUrl match
            case Some(baseUrl) => YouTrackClient.fetchIssue(issueId, baseUrl, token)
            case None => Left(s"YouTrack base URL not configured. Add 'baseUrl' to tracker section in ${Constants.Paths.ConfigFile}")

    case IssueTrackerType.GitHub =>
      config.repository match
        case None =>
          Left("GitHub repository not configured. Run 'iw init' first.")
        case Some(repository) =>
          // Pass full issue ID (e.g., "IWCLI-51") - client extracts number for API
          GitHubClient.fetchIssue(issueId.value, repository)

    case IssueTrackerType.GitLab =>
      config.repository match
        case None =>
          Left("GitLab repository not configured. Run 'iw init' first.")
        case Some(repository) =>
          // Pass full issue ID (e.g., "PROJ-123") - client extracts number for API
          GitLabClient.fetchIssue(issueId.value, repository) match
            case Left(error) if GitLabClient.isNotFoundError(error) =>
              Left(GitLabClient.formatIssueNotFoundError(issueId.value, repository))
            case Left(error) if GitLabClient.isAuthenticationError(error) =>
              Left(GitLabClient.formatGlabNotAuthenticatedError())
            case Left(error) if GitLabClient.isNetworkError(error) =>
              Left(GitLabClient.formatNetworkError(error))
            case result => result
