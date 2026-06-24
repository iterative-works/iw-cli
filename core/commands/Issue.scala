// PURPOSE: Issue command logic: fetch+display, or create subcommand for tracker-specific creation
// PURPOSE: Dispatches across Linear/YouTrack/GitHub/GitLab via TrackerOps and EnvVars

package iw.core.commands

import iw.core.adapters.GitLabClient
import iw.core.model.{
  ApiToken,
  ConfigSerializer,
  Constants,
  IssueCreateParser,
  IssueId,
  IssueTrackerType,
  ProjectConfiguration,
  WorktreePath
}
import iw.core.output.IssueFormatter

object Issue:
  def run(args: Seq[String], env: CommandEnv): CommandResult =
    if args.nonEmpty && args.head == "create" then runCreate(args.tail, env)
    else runFetch(args, env)

  private def runFetch(args: Seq[String], env: CommandEnv): CommandResult =
    val outcome =
      for
        config <- readConfig(env)
        issueId <- resolveIssueId(args, config, env)
        issue <- fetchIssue(issueId, config, env)
      yield (issue, issueId, config)

    outcome match
      case Right((issue, issueId, config)) =>
        env.console.out(IssueFormatter.format(issue))
        updateLastSeen(issueId, config, env)
        CommandResult.ok
      case Left(error) =>
        env.console.err(s"Error: $error")
        CommandResult.error

  private def runCreate(args: Seq[String], env: CommandEnv): CommandResult =
    if args.contains("--help") || args.contains("-h") then
      printCreateHelp(env)
      CommandResult.ok
    else
      IssueCreateParser.parse(args) match
        case Left(error) =>
          env.console.err(s"Error: $error")
          env.console.out("")
          printCreateHelp(env)
          CommandResult.error
        case Right(request) =>
          readConfig(env) match
            case Left(err) =>
              env.console.err(s"Error: $err")
              CommandResult.error
            case Right(config) =>
              createIssue(
                request.title,
                request.description.getOrElse(""),
                config,
                env
              )

  private def readConfig(
      env: CommandEnv
  ): Either[String, ProjectConfiguration] =
    val configPath = env.cwd / Constants.Paths.IwDir / "config.conf"
    if !env.fs.exists(configPath) then
      Left("Configuration file not found. Run 'iw init' first.")
    else
      env.fs
        .read(configPath)
        .flatMap(ConfigSerializer.fromHocon)
        .left
        .map(_ => "Configuration file not found. Run 'iw init' first.")

  private def resolveIssueId(
      args: Seq[String],
      config: ProjectConfiguration,
      env: CommandEnv
  ): Either[String, IssueId] =
    if args.isEmpty then
      for
        branch <- env.git.getCurrentBranch(env.cwd)
        issueId <- IssueId.fromBranch(branch)
      yield issueId
    else
      val teamPrefix = config.trackerType match
        case IssueTrackerType.GitHub | IssueTrackerType.GitLab =>
          config.teamPrefix
        case _ => None
      IssueId.parse(args.head, teamPrefix)

  private def gitlabHost(env: CommandEnv): Option[String] =
    env.git.getRemoteUrl(env.cwd).flatMap(_.host.toOption)

  private def linearToken(env: CommandEnv): Either[String, ApiToken] =
    env.envVars
      .get(Constants.EnvVars.LinearApiToken)
      .flatMap(ApiToken.apply)
      .toRight(
        s"${Constants.EnvVars.LinearApiToken} environment variable is not set"
      )

  private def youtrackToken(env: CommandEnv): Either[String, ApiToken] =
    env.envVars
      .get(Constants.EnvVars.YouTrackApiToken)
      .flatMap(ApiToken.apply)
      .toRight(
        s"${Constants.EnvVars.YouTrackApiToken} environment variable is not set"
      )

  private def fetchIssue(
      issueId: IssueId,
      config: ProjectConfiguration,
      env: CommandEnv
  ): Either[String, iw.core.model.Issue] =
    config.trackerType match
      case IssueTrackerType.Linear =>
        for
          token <- linearToken(env)
          issue <- env.tracker.fetchLinearIssue(issueId, token)
        yield issue

      case IssueTrackerType.YouTrack =>
        for
          token <- youtrackToken(env)
          baseUrl <- config.trackerBaseUrl.toRight(
            s"YouTrack base URL not configured. Add 'baseUrl' to tracker section in ${Constants.Paths.ConfigFile}"
          )
          issue <- env.tracker.fetchYouTrackIssue(issueId, baseUrl, token)
        yield issue

      case IssueTrackerType.GitHub =>
        config.repository match
          case None =>
            Left("GitHub repository not configured. Run 'iw init' first.")
          case Some(repository) =>
            env.tracker.fetchGitHubIssue(issueId.value, repository)

      case IssueTrackerType.GitLab =>
        config.repository match
          case None =>
            Left("GitLab repository not configured. Run 'iw init' first.")
          case Some(repository) =>
            env.tracker.fetchGitLabIssue(
              issueId.value,
              repository,
              gitlabHost(env)
            ) match
              case Left(error) if GitLabClient.isNotFoundError(error) =>
                Left(
                  GitLabClient
                    .formatIssueNotFoundError(issueId.value, repository)
                )
              case Left(error) if GitLabClient.isAuthenticationError(error) =>
                Left(GitLabClient.formatGlabNotAuthenticatedError())
              case Left(error) if GitLabClient.isNetworkError(error) =>
                Left(GitLabClient.formatNetworkError(error))
              case result => result

      case IssueTrackerType.Forgejo =>
        Left("Forgejo issue fetch is not yet implemented")

  private def updateLastSeen(
      issueId: IssueId,
      config: ProjectConfiguration,
      env: CommandEnv
  ): Unit =
    val worktreePath = WorktreePath(config.projectName, issueId)
    val targetPath = worktreePath.resolve(env.cwd)
    env.server.updateLastSeen(
      issueId.value,
      targetPath.toString,
      config.trackerType.toString,
      issueId.team
    )
    ()

  private def createIssue(
      title: String,
      description: String,
      config: ProjectConfiguration,
      env: CommandEnv
  ): CommandResult =
    val result: Either[String, iw.core.adapters.CreatedIssue] =
      config.trackerType match
        case IssueTrackerType.GitHub =>
          config.repository match
            case None =>
              Left("GitHub repository not configured. Run 'iw init' first.")
            case Some(repository) =>
              env.tracker.createGitHubIssue(repository, title, description)

        case IssueTrackerType.GitLab =>
          config.repository match
            case None =>
              Left("GitLab repository not configured. Run 'iw init' first.")
            case Some(repository) =>
              env.tracker.createGitLabIssue(
                repository,
                title,
                description,
                gitlabHost(env)
              )

        case IssueTrackerType.Linear =>
          for
            token <- linearToken(env)
            created <- env.tracker
              .createLinearIssue(title, description, config.team, token)
          yield created

        case IssueTrackerType.YouTrack =>
          for
            token <- youtrackToken(env)
            baseUrl <- config.trackerBaseUrl.toRight(
              s"YouTrack base URL not configured. Add 'baseUrl' to tracker section in ${Constants.Paths.ConfigFile}"
            )
            created <- env.tracker.createYouTrackIssue(
              config.team,
              title,
              description,
              baseUrl,
              token
            )
          yield created

        case IssueTrackerType.Forgejo =>
          Left("Forgejo issue creation is not yet implemented")

    result match
      case Left(error) =>
        env.console.err(s"Error: Failed to create issue: $error")
        CommandResult.error
      case Right(created) =>
        env.console.out(s"✓ Issue created: #${created.id}")
        env.console.out(s"URL: ${created.url}")
        CommandResult.ok

  private def printCreateHelp(env: CommandEnv): Unit =
    val lines = List(
      "Create a new issue in the configured tracker",
      "",
      "Usage:",
      "  iw issue create --title \"Issue title\" [--description \"Details\"]",
      "",
      "Arguments:",
      "  --title        Issue title (required)",
      "  --description  Detailed description (optional)",
      "",
      "Examples:",
      "  iw issue create --title \"Bug in start command\"",
      "  iw issue create --title \"Feature request\" --description \"Would be nice to have X\""
    )
    lines.foreach(env.console.out)
