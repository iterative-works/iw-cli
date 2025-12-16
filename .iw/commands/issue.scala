// PURPOSE: Fetch and display issue information from the tracker
// USAGE: iw issue [issue-id]
// ARGS:
//   [issue-id]: Optional issue identifier to fetch. If not provided, infers from current branch
// EXAMPLE: iw issue IWLE-123

import iw.core.*

@main def issue(args: String*): Unit =
  val result = for {
    issueId <- getIssueId(args)
    config <- loadConfig()
    issue <- fetchIssue(issueId, config)
  } yield issue

  result match
    case Right(issue) =>
      val formatted = IssueFormatter.format(issue)
      println(formatted)
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
      val token = sys.env.getOrElse(Constants.EnvVars.LinearApiToken, "")
      if token.isEmpty then
        Left(s"${Constants.EnvVars.LinearApiToken} environment variable is not set")
      else
        LinearClient.fetchIssue(issueId, token)

    case IssueTrackerType.YouTrack =>
      val token = sys.env.getOrElse(Constants.EnvVars.YouTrackApiToken, "")
      if token.isEmpty then
        Left(s"${Constants.EnvVars.YouTrackApiToken} environment variable is not set")
      else
        config.youtrackBaseUrl match
          case Some(baseUrl) => YouTrackClient.fetchIssue(issueId, baseUrl, token)
          case None => Left(s"YouTrack base URL not configured. Add 'baseUrl' to tracker section in ${Constants.Paths.ConfigFile}")
