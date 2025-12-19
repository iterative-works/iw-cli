// PURPOSE: Submit feedback to iw-cli team via Linear issue creation
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

  // Get LINEAR_API_TOKEN from environment
  val token = ApiToken.fromEnv(Constants.EnvVars.LinearApiToken) match
    case None =>
      Output.error(s"${Constants.EnvVars.LinearApiToken} environment variable is not set")
      Output.info("Please set your Linear API token to submit feedback")
      sys.exit(1)
    case Some(t) => t

  // Parse arguments
  val request = FeedbackParser.parseFeedbackArgs(args.toSeq) match
    case Left(error) =>
      Output.error(error)
      Output.info("Run 'iw feedback --help' for usage information")
      sys.exit(1)
    case Right(r) => r

  // Map issue type to Linear label ID
  val labelId = request.issueType match
    case FeedbackParser.IssueType.Bug => Constants.IwCliLabels.Bug
    case FeedbackParser.IssueType.Feature => Constants.IwCliLabels.Feature

  // Create issue via Linear API
  val result = LinearClient.createIssue(
    title = request.title,
    description = request.description,
    teamId = Constants.IwCliTeamId,
    token = token,
    labelIds = Seq(labelId)
  )

  result match
    case Left(error) =>
      Output.error(s"Failed to create issue: $error")
      sys.exit(1)
    case Right(created) =>
      Output.success("Feedback submitted successfully!")
      Output.info(s"Issue ID: ${created.id}")
      Output.info(s"URL: ${created.url}")
      sys.exit(0)

def showHelp(): Unit =
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
