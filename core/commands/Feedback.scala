// PURPOSE: Feedback command logic: parse request + open GitHub issue against iw-cli repo
// PURPOSE: Handles --help, validation, and createFeedbackIssue result reporting

package iw.core.commands

import iw.core.model.{Constants, FeedbackParser}

object Feedback:
  def run(args: Seq[String], env: CommandEnv): CommandResult =
    if args.contains("--help") || args.contains("-h") then
      printHelp(env)
      CommandResult.ok
    else
      FeedbackParser.parseFeedbackArgs(args) match
        case Left(error) =>
          env.console.err(s"Error: $error")
          env.console.out("Run 'iw feedback --help' for usage information")
          CommandResult.error
        case Right(request) =>
          submit(request, env)

  private def submit(
      request: FeedbackParser.FeedbackRequest,
      env: CommandEnv
  ): CommandResult =
    env.tracker.createFeedbackIssue(
      repository = Constants.Feedback.Repository,
      title = request.title,
      description = request.description,
      issueType = request.issueType
    ) match
      case Left(error) =>
        env.console.err(s"Error: Failed to create issue: $error")
        CommandResult.error
      case Right(created) =>
        env.console.out(s"✓ Feedback submitted successfully!")
        env.console.out(s"Issue: #${created.id}")
        env.console.out(s"URL: ${created.url}")
        CommandResult.ok

  private def printHelp(env: CommandEnv): Unit =
    val lines = List(
      "Submit feedback to the iw-cli team",
      "",
      "Usage:",
      "  iw feedback \"Issue title\" [--description \"Details\"] [--type bug|feature]",
      "",
      "Arguments:",
      "  title          Issue title (required, can be multiple words)",
      "  --description  Detailed description (optional)",
      "  --type         Issue type: 'bug' or 'feature' (optional, default: feature)",
      "",
      "Examples:",
      "  iw feedback \"Bug in start command\"",
      "  iw feedback \"Feature request\" --description \"Would be nice to have X\"",
      "  iw feedback \"Command crashes\" --type bug --description \"When I run Y, it fails\""
    )
    lines.foreach(env.console.out)
