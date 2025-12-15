// PURPOSE: Fetch and display issue information from the tracker
// USAGE: iw issue [issue-id]
// ARGS:
//   [issue-id]: Optional issue identifier to fetch. If not provided, infers from current branch
// EXAMPLE: iw issue IWLE-123

import iw.core.*
import java.nio.file.{Path, Paths}

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
    val currentDir = Paths.get(System.getProperty("user.dir"))
    for {
      branch <- GitAdapter.getCurrentBranch(currentDir)
      issueId <- IssueId.fromBranch(branch)
    } yield issueId
  else
    // Parse explicit issue ID
    IssueId.parse(args.head)

def loadConfig(): Either[String, ProjectConfiguration] =
  val configPath = Paths.get(System.getProperty("user.dir"), ".iw", "config.conf")
  ConfigFileRepository.read(configPath) match
    case Some(config) => Right(config)
    case None => Left("Configuration file not found. Run 'iw init' first.")

def fetchIssue(issueId: IssueId, config: ProjectConfiguration): Either[String, Issue] =
  config.trackerType match
    case IssueTrackerType.Linear =>
      val token = sys.env.getOrElse("LINEAR_API_TOKEN", "")
      if token.isEmpty then
        Left("LINEAR_API_TOKEN environment variable is not set")
      else
        LinearClient.fetchIssue(issueId, token)

    case IssueTrackerType.YouTrack =>
      val token = sys.env.getOrElse("YOUTRACK_API_TOKEN", "")
      if token.isEmpty then
        Left("YOUTRACK_API_TOKEN environment variable is not set")
      else
        val baseUrl = "https://youtrack.e-bs.cz"
        YouTrackClient.fetchIssue(issueId, baseUrl, token)
