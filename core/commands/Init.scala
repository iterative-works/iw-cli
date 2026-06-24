// PURPOSE: Init command logic: discover/prompt tracker config, write .iw/config.conf
// PURPOSE: Per-tracker collection (Linear, YouTrack, GitHub, GitLab); auto-detects from git remote

package iw.core.commands

import iw.core.model.{
  Constants,
  IssueTrackerType,
  ProjectConfiguration,
  TeamPrefixValidator,
  TrackerDetector
}

object Init:
  def run(args: Seq[String], env: CommandEnv): CommandResult =
    val force = args.contains("--force")
    val trackerArg = parseArg(args, "--tracker=")
    val teamArg = parseArg(args, "--team=")
    val repositoryArg = parseArg(args, "--repository=")
    val teamPrefixArg = parseArg(args, "--team-prefix=")
    val baseUrlArg = parseArg(args, "--base-url=")

    if !env.git.isRepository(env.cwd) then
      env.console.err(
        "Error: Not in a git repository. Please run 'git init' first."
      )
      CommandResult.error
    else
      val configPath = env.cwd / Constants.Paths.IwDir / "config.conf"
      if env.fs.exists(configPath) && !force then
        env.console.err(
          s"Error: Configuration already exists at ${Constants.Paths.ConfigFile}"
        )
        env.console.out("Use 'iw init --force' to overwrite")
        CommandResult.error
      else
        resolveTrackerType(trackerArg, env) match
          case Left(err) =>
            env.console.err(s"Error: $err")
            CommandResult.error
          case Right(trackerType) =>
            collectTrackerDetails(
              trackerType,
              teamArg,
              repositoryArg,
              teamPrefixArg,
              baseUrlArg,
              env
            ) match
              case Left(err) =>
                env.console.err(s"Error: $err")
                CommandResult.error
              case Right((team, repository, teamPrefix, trackerBaseUrl)) =>
                writeConfig(
                  configPath,
                  trackerType,
                  team,
                  repository,
                  teamPrefix,
                  trackerBaseUrl,
                  env
                )

  private def parseArg(args: Seq[String], prefix: String): Option[String] =
    args.find(_.startsWith(prefix)).map(_.drop(prefix.length))

  private def resolveTrackerType(
      trackerArg: Option[String],
      env: CommandEnv
  ): Either[String, IssueTrackerType] =
    trackerArg match
      case Some(Constants.TrackerTypeValues.Linear) =>
        Right(IssueTrackerType.Linear)
      case Some(Constants.TrackerTypeValues.YouTrack) =>
        Right(IssueTrackerType.YouTrack)
      case Some(Constants.TrackerTypeValues.GitHub) =>
        Right(IssueTrackerType.GitHub)
      case Some(Constants.TrackerTypeValues.GitLab) =>
        Right(IssueTrackerType.GitLab)
      case Some(invalid) =>
        Left(
          s"Invalid tracker type: $invalid. Use '${Constants.TrackerTypeValues.Linear}', '${Constants.TrackerTypeValues.YouTrack}', '${Constants.TrackerTypeValues.GitHub}', or '${Constants.TrackerTypeValues.GitLab}'."
        )
      case None =>
        Right(detectOrAskTracker(env))

  private def detectOrAskTracker(env: CommandEnv): IssueTrackerType =
    val remote = env.git.getRemoteUrl(env.cwd)
    val suggested = remote.flatMap(TrackerDetector.suggestTracker)
    suggested match
      case Some(s) =>
        env.console.out(
          s"Detected tracker: ${trackerName(s)} (based on git remote)"
        )
        if env.prompt.confirm(s"Use ${trackerName(s)}?", default = true) then s
        else askForTracker(env)
      case None =>
        env.console.out("Could not detect tracker from git remote")
        askForTracker(env)

  private def trackerName(t: IssueTrackerType): String = t match
    case IssueTrackerType.Linear   => Constants.TrackerTypeValues.Linear
    case IssueTrackerType.YouTrack => Constants.TrackerTypeValues.YouTrack
    case IssueTrackerType.GitHub   => Constants.TrackerTypeValues.GitHub
    case IssueTrackerType.GitLab   => Constants.TrackerTypeValues.GitLab
    case IssueTrackerType.Forgejo  => Constants.TrackerTypeValues.Forgejo

  private def askForTracker(env: CommandEnv): IssueTrackerType =
    env.console.out("Available trackers:")
    env.console.out("  1. Linear")
    env.console.out("  2. YouTrack")
    env.console.out("  3. GitHub")
    env.console.out("  4. GitLab")
    env.prompt.ask("Select tracker (1, 2, 3, or 4)") match
      case "1" | "linear"   => IssueTrackerType.Linear
      case "2" | "youtrack" => IssueTrackerType.YouTrack
      case "3" | "github"   => IssueTrackerType.GitHub
      case "4" | "gitlab"   => IssueTrackerType.GitLab
      case _                =>
        env.console.err("Error: Invalid choice. Please select 1, 2, 3, or 4.")
        askForTracker(env)

  private def collectTrackerDetails(
      trackerType: IssueTrackerType,
      teamArg: Option[String],
      repositoryArg: Option[String],
      teamPrefixArg: Option[String],
      baseUrlArg: Option[String],
      env: CommandEnv
  ): Either[String, (String, Option[String], Option[String], Option[String])] =
    trackerType match
      case IssueTrackerType.GitHub =>
        val ownerRepo = resolveGitHubRepo(repositoryArg, env)
        resolveTeamPrefix(teamPrefixArg, ownerRepo, env).map { prefix =>
          ("", Some(ownerRepo), Some(prefix), None)
        }

      case IssueTrackerType.GitLab =>
        val ownerRepo = resolveGitLabRepo(repositoryArg, env)
        resolveTeamPrefix(teamPrefixArg, ownerRepo, env).map { prefix =>
          val baseUrl = resolveGitLabBaseUrl(baseUrlArg, env)
          ("", Some(ownerRepo), Some(prefix), baseUrl)
        }

      case IssueTrackerType.YouTrack =>
        val team = teamArg.getOrElse(
          env.prompt.ask("Enter team/project identifier (e.g., MEDH, IWSD)")
        )
        val baseUrl = baseUrlArg.getOrElse(
          env.prompt.ask(
            "Enter YouTrack base URL (e.g., https://youtrack.example.com)"
          )
        )
        Right((team, None, None, Some(baseUrl)))

      case IssueTrackerType.Linear =>
        val team = teamArg.getOrElse(
          env.prompt.ask("Enter team/project identifier (e.g., IWLE, TEST)")
        )
        Right((team, None, None, None))

      case IssueTrackerType.Forgejo =>
        Left("Forgejo tracker support is not yet implemented in init")

  private def resolveGitHubRepo(
      repositoryArg: Option[String],
      env: CommandEnv
  ): String =
    repositoryArg.getOrElse {
      val remote = env.git.getRemoteUrl(env.cwd)
      val detected = remote.flatMap { r =>
        r.repositoryOwnerAndName match
          case Right(ownerRepo) => Some(ownerRepo)
          case Left(err)        =>
            env.console.out(s"Warning: Could not auto-detect repository: $err")
            None
      }
      detected match
        case Some(ownerRepo) =>
          env.console.out(s"Auto-detected repository: $ownerRepo")
          ownerRepo
        case None =>
          env.prompt.ask("Enter GitHub repository (owner/repo format)")
    }

  private def resolveGitLabRepo(
      repositoryArg: Option[String],
      env: CommandEnv
  ): String =
    repositoryArg.getOrElse {
      val remote = env.git.getRemoteUrl(env.cwd)
      val detected = remote.flatMap { r =>
        r.extractGitLabRepository match
          case Right(ownerRepo) => Some(ownerRepo)
          case Left(err)        =>
            env.console.out(s"Warning: Could not auto-detect repository: $err")
            None
      }
      detected match
        case Some(ownerRepo) =>
          env.console.out(s"Auto-detected repository: $ownerRepo")
          ownerRepo
        case None =>
          env.prompt.ask(
            "Enter GitLab repository (owner/repo or group/subgroup/project format)"
          )
    }

  private def resolveGitLabBaseUrl(
      baseUrlArg: Option[String],
      env: CommandEnv
  ): Option[String] =
    baseUrlArg match
      case Some(url) => Some(url)
      case None      =>
        env.git.getRemoteUrl(env.cwd).flatMap(_.host.toOption) match
          case Some(host) if host != "gitlab.com" =>
            env.console.out(s"Detected self-hosted GitLab at $host")
            Some(env.prompt.ask(s"Enter GitLab base URL (e.g., https://$host)"))
          case _ => None

  private def resolveTeamPrefix(
      teamPrefixArg: Option[String],
      ownerRepo: String,
      env: CommandEnv
  ): Either[String, String] =
    teamPrefixArg match
      case Some(p) =>
        TeamPrefixValidator
          .validate(p)
          .left
          .map(err => s"Invalid team prefix: $err")
      case None =>
        val suggested = TeamPrefixValidator.suggestFromRepository(ownerRepo)
        env.console.out(s"Suggested team prefix: $suggested")
        val input = env.prompt.ask(
          s"Enter team prefix (2-10 uppercase letters) [$suggested]"
        )
        val chosen = if input.trim.isEmpty then suggested else input.trim
        TeamPrefixValidator
          .validate(chosen)
          .left
          .map(err => s"Invalid team prefix: $err")

  private def writeConfig(
      configPath: os.Path,
      trackerType: IssueTrackerType,
      team: String,
      repository: Option[String],
      teamPrefix: Option[String],
      trackerBaseUrl: Option[String],
      env: CommandEnv
  ): CommandResult =
    val projectName = env.cwd.last
    val config = ProjectConfiguration.create(
      trackerType = trackerType,
      team = team,
      projectName = projectName,
      repository = repository,
      teamPrefix = teamPrefix,
      trackerBaseUrl = trackerBaseUrl
    )
    env.config.write(configPath, config) match
      case Left(err) =>
        env.console.err(s"Error: $err")
        CommandResult.error
      case Right(_) =>
        env.console.out(
          s"✓ Configuration created at ${Constants.Paths.ConfigFile}"
        )
        env.console.out("")
        env.console.out("=== Next steps ===")
        printNextSteps(trackerType, env)
        env.console.out("")
        env.console.out("Run './iw doctor' to verify your setup.")
        CommandResult.ok

  private def printNextSteps(
      trackerType: IssueTrackerType,
      env: CommandEnv
  ): Unit =
    trackerType match
      case IssueTrackerType.Linear =>
        env.console.out("Set your API token:")
        env.console.out(
          s"  export ${Constants.EnvVars.LinearApiToken}=lin_api_..."
        )
      case IssueTrackerType.YouTrack =>
        env.console.out("Set your API token:")
        env.console.out(
          s"  export ${Constants.EnvVars.YouTrackApiToken}=perm:..."
        )
      case IssueTrackerType.GitHub =>
        env.console.out("GitHub tracker configured.")
        env.console.out("Ensure the gh CLI is installed and authenticated:")
        env.console.out("  gh auth login")
      case IssueTrackerType.GitLab =>
        env.console.out("GitLab tracker configured.")
        env.console.out("Ensure the glab CLI is installed and authenticated:")
        env.console.out("  brew install glab  (macOS)")
        env.console.out("  glab auth login")
        env.console.out("")
        env.console.out(
          "For other platforms, see: https://gitlab.com/gitlab-org/cli"
        )
      case IssueTrackerType.Forgejo => ()
