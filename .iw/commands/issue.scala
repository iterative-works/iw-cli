// PURPOSE: Fetch and display issue information from the tracker
// USAGE: iw issue [issue-id]
// ARGS:
//   [issue-id]: Optional issue identifier to fetch. If not provided, infers from current branch
// EXAMPLE: iw issue IWLE-123

import iw.core.model.*
import iw.core.adapters.*
import iw.core.output.*
import iw.core.dashboard.ServerClient

@main def issue(args: String*): Unit =
  // Handle subcommands
  if args.nonEmpty && args(0) == "create" then
    handleCreateSubcommand(args.tail)
  else
    // Default behavior: fetch and display issue
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

def handleCreateSubcommand(args: Seq[String]): Unit =
  // Handle --help flag
  if args.contains("--help") || args.contains("-h") then
    showCreateHelp()
    sys.exit(0)

  // Parse arguments
  IssueCreateParser.parse(args) match
    case Left(error) =>
      Output.error(error)
      println()
      showCreateHelp()
      sys.exit(1)
    case Right(request) =>
      // Load configuration
      loadConfig() match
        case Left(error) =>
          Output.error(error)
          sys.exit(1)
        case Right(config) =>
          val description = request.description.getOrElse("")

          config.trackerType match
            case IssueTrackerType.GitHub =>
              createGitHubIssue(request.title, description, config)

            case IssueTrackerType.Linear =>
              createLinearIssue(request.title, description, config)

            case IssueTrackerType.GitLab =>
              createGitLabIssue(request.title, description, config)

            case IssueTrackerType.YouTrack =>
              createYouTrackIssue(request.title, description, config)

private def showCreateHelp(): Unit =
  println("Create a new issue in the configured tracker")
  println()
  println("Usage:")
  println("  iw issue create --title \"Issue title\" [--description \"Details\"]")
  println()
  println("Arguments:")
  println("  --title        Issue title (required)")
  println("  --description  Detailed description (optional)")
  println()
  println("Examples:")
  println("  iw issue create --title \"Bug in start command\"")
  println("  iw issue create --title \"Feature request\" --description \"Would be nice to have X\"")

private def createGitHubIssue(title: String, description: String, config: ProjectConfiguration): Unit =
  val repository = config.repository.getOrElse {
    Output.error("GitHub repository not configured. Run 'iw init' first.")
    sys.exit(1)
  }

  GitHubClient.createIssue(repository, title, description) match
    case Left(error) =>
      Output.error(s"Failed to create issue: $error")
      sys.exit(1)
    case Right(createdIssue) =>
      Output.success(s"Issue created: #${createdIssue.id}")
      Output.info(s"URL: ${createdIssue.url}")
      sys.exit(0)

private def createLinearIssue(title: String, description: String, config: ProjectConfiguration): Unit =
  ApiToken.fromEnv(Constants.EnvVars.LinearApiToken) match
    case None =>
      Output.error(s"${Constants.EnvVars.LinearApiToken} environment variable is not set")
      sys.exit(1)
    case Some(token) =>
      val teamId = config.team
      LinearClient.createIssue(title, description, teamId, token) match
        case Left(error) =>
          Output.error(s"Failed to create issue: $error")
          sys.exit(1)
        case Right(createdIssue) =>
          Output.success(s"Issue created: #${createdIssue.id}")
          Output.info(s"URL: ${createdIssue.url}")
          sys.exit(0)

private def createGitLabIssue(title: String, description: String, config: ProjectConfiguration): Unit =
  val repository = config.repository.getOrElse {
    Output.error("GitLab repository not configured. Run 'iw init' first.")
    sys.exit(1)
  }

  GitLabClient.createIssue(repository, title, description) match
    case Left(error) =>
      Output.error(s"Failed to create issue: $error")
      sys.exit(1)
    case Right(createdIssue) =>
      Output.success(s"Issue created: #${createdIssue.id}")
      Output.info(s"URL: ${createdIssue.url}")
      sys.exit(0)

private def createYouTrackIssue(title: String, description: String, config: ProjectConfiguration): Unit =
  val baseUrl = config.youtrackBaseUrl.getOrElse {
    Output.error(s"YouTrack base URL not configured. Add 'baseUrl' to tracker section in ${Constants.Paths.ConfigFile}")
    sys.exit(1)
  }

  val project = config.team

  ApiToken.fromEnv(Constants.EnvVars.YouTrackApiToken) match
    case None =>
      Output.error(s"${Constants.EnvVars.YouTrackApiToken} environment variable is not set")
      sys.exit(1)
    case Some(token) =>
      YouTrackClient.createIssue(project, title, description, baseUrl, token) match
        case Left(error) =>
          Output.error(s"Failed to create issue: $error")
          sys.exit(1)
        case Right(createdIssue) =>
          Output.success(s"Issue created: ${createdIssue.id}")
          Output.info(s"URL: ${createdIssue.url}")
          sys.exit(0)

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
