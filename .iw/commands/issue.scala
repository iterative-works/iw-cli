// PURPOSE: Fetch and display issue information from the tracker
// USAGE: iw issue [issue-id]
// ARGS:
//   [issue-id]: Optional issue identifier to fetch. If not provided, infers from current branch
// EXAMPLE: iw issue IWLE-123

import iw.core.*
import iw.core.infrastructure.ServerClient

@main def issue(args: String*): Unit =
  val result = for {
    issueId <- getIssueId(args)
    config <- loadConfig()
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

def getIssueId(args: Seq[String]): Either[String, IssueId] =
  if args.isEmpty then
    // Infer from current branch
    val currentDir = os.Path(System.getProperty(Constants.SystemProps.UserDir))
    for {
      branch <- GitAdapter.getCurrentBranch(currentDir)
      issueId <- IssueId.fromBranch(branch)
    } yield issueId
  else
    // Parse explicit issue ID
    IssueId.parse(args.head)

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
          // Extract numeric issue number from IssueId
          // Handle both numeric GitHub IDs (e.g., "132") and potential TEAM-NNN format
          val issueNumber = if issueId.value.contains("-") then
            issueId.value.split("-")(1) // IWLE-132 -> 132 (shouldn't happen for GitHub, but handle it)
          else
            issueId.value // 132 -> 132

          GitHubClient.fetchIssue(issueNumber, repository)
