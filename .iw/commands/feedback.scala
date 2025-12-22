// PURPOSE: Submit feedback to iw-cli team via issue tracker
// PURPOSE: Allows users and agents to report bugs or request features
// USAGE: iw feedback "Issue title" [--description "Details"] [--type bug|feature]
// ARGS:
//   title: Issue title (required, can be multiple words)
//   --description: Detailed description (optional)
//   --type: Issue type - 'bug' or 'feature' (optional, defaults to 'feature')
// EXAMPLES:
//   iw feedback "Bug in start command"
//   iw feedback "Feature request" --description "Would be nice to have X"
//   iw feedback "Command crashes" --type bug --description "When I run Y, it fails"

import iw.core.*

@main def feedback(args: String*): Unit =
  // Handle --help flag
  if args.contains("--help") || args.contains("-h") then
    showHelp()
    sys.exit(0)

  // Load config to determine tracker type
  val config = loadConfig() match
    case Left(error) =>
      Output.error(error)
      sys.exit(1)
    case Right(c) => c

  // Parse arguments
  val request = FeedbackParser.parseFeedbackArgs(args.toSeq) match
    case Left(error) =>
      Output.error(error)
      Output.info("Run 'iw feedback --help' for usage information")
      sys.exit(1)
    case Right(r) => r

  // Route to appropriate client based on tracker type
  val result = config.trackerType match
    case IssueTrackerType.GitHub =>
      createGitHubIssue(config, request)

    case IssueTrackerType.Linear =>
      createLinearIssue(request)

    case IssueTrackerType.YouTrack =>
      Left("YouTrack feedback not yet supported")

  result match
    case Left(error) =>
      Output.error(s"Failed to create issue: $error")
      sys.exit(1)
    case Right(created) =>
      Output.success("Feedback submitted successfully!")
      Output.info(s"Issue: #${created.id}")
      Output.info(s"URL: ${created.url}")
      sys.exit(0)

def loadConfig(): Either[String, ProjectConfiguration] =
  val configPath = os.Path(System.getProperty(Constants.SystemProps.UserDir)) / Constants.Paths.IwDir / "config.conf"

  if !os.exists(configPath) then
    return Left("Configuration file not found. Run 'iw init' first.")

  val hocon = os.read(configPath)
  ConfigSerializer.fromHocon(hocon) match
    case Left(error) => Left(s"Failed to read config: $error")
    case Right(config) => Right(config)

def createGitHubIssue(config: ProjectConfiguration, request: FeedbackParser.FeedbackRequest): Either[String, CreatedIssue] =
  // Get repository from config
  config.repository match
    case None =>
      Left("GitHub repository not configured. Add 'repository' to tracker section in config.")
    case Some(repo) =>
      GitHubClient.createIssue(
        repository = repo,
        title = request.title,
        description = request.description,
        issueType = request.issueType
      )

def createLinearIssue(request: FeedbackParser.FeedbackRequest): Either[String, CreatedIssue] =
  // Get LINEAR_API_TOKEN from environment
  val token = ApiToken.fromEnv(Constants.EnvVars.LinearApiToken) match
    case None =>
      return Left(s"${Constants.EnvVars.LinearApiToken} environment variable is not set")
    case Some(t) => t

  // Map issue type to Linear label ID
  val labelId = FeedbackParser.getLabelIdForIssueType(request.issueType)

  // Create issue via Linear API
  LinearClient.createIssue(
    title = request.title,
    description = request.description,
    teamId = Constants.IwCliTeamId,
    token = token,
    labelIds = Seq(labelId)
  )

private def showHelp(): Unit =
  println("Submit feedback to the iw-cli team")
  println()
  println("Usage:")
  println("  iw feedback \"Issue title\" [--description \"Details\"] [--type bug|feature]")
  println()
  println("Arguments:")
  println("  title          Issue title (required, can be multiple words)")
  println("  --description  Detailed description (optional)")
  println("  --type         Issue type: 'bug' or 'feature' (optional, default: feature)")
  println()
  println("Environment:")
  println(s"  ${Constants.EnvVars.LinearApiToken}  Your Linear API token (required)")
  println()
  println("Examples:")
  println("  iw feedback \"Bug in start command\"")
  println("  iw feedback \"Feature request\" --description \"Would be nice to have X\"")
  println("  iw feedback \"Command crashes\" --type bug --description \"When I run Y, it fails\"")
