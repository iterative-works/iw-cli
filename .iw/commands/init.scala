// PURPOSE: Initialize iw-cli configuration for the project
// USAGE: iw init [--force] [--tracker=linear|youtrack|github|gitlab] [--team=TEAM] [--repository=OWNER/REPO] [--team-prefix=PREFIX] [--base-url=URL]
// ARGS:
//   --force: Overwrite existing configuration
//   --tracker=linear|youtrack|github|gitlab: Set tracker type (skips prompt)
//   --team=TEAM: Set team identifier for Linear/YouTrack (skips prompt)
//   --repository=OWNER/REPO: Set GitHub/GitLab repository (skips prompt)
//   --team-prefix=PREFIX: Set team prefix for GitHub/GitLab (skips prompt)
//   --base-url=URL: Set YouTrack/GitLab base URL (skips prompt)
// EXAMPLE: iw init
// EXAMPLE: iw init --tracker=linear --team=IWLE
// EXAMPLE: iw init --tracker=github --repository=owner/repo --team-prefix=IWCLI
// EXAMPLE: iw init --tracker=gitlab --repository=owner/repo --team-prefix=PROJ --base-url=https://gitlab.company.com
// EXAMPLE: iw init --tracker=youtrack --team=MEDH --base-url=https://youtrack.example.com

import iw.core.model.*
import iw.core.adapters.*
import iw.core.output.*

def parseArg(args: Seq[String], prefix: String): Option[String] =
  args.find(_.startsWith(prefix)).map(_.drop(prefix.length))

def askForTrackerType(): IssueTrackerType =
  Output.info("Available trackers:")
  Output.info("  1. Linear")
  Output.info("  2. YouTrack")
  Output.info("  3. GitHub")
  Output.info("  4. GitLab")

  val choice = Prompt.ask("Select tracker (1, 2, 3, or 4)")

  choice match
    case "1" | "linear" => IssueTrackerType.Linear
    case "2" | "youtrack" => IssueTrackerType.YouTrack
    case "3" | "github" => IssueTrackerType.GitHub
    case "4" | "gitlab" => IssueTrackerType.GitLab
    case _ =>
      Output.error("Invalid choice. Please select 1, 2, 3, or 4.")
      askForTrackerType()

@main def init(args: String*): Unit =
  val force = args.contains("--force")
  val trackerArg = parseArg(args, "--tracker=")
  val teamArg = parseArg(args, "--team=")
  val repositoryArg = parseArg(args, "--repository=")
  val teamPrefixArg = parseArg(args, "--team-prefix=")
  val baseUrlArg = parseArg(args, "--base-url=")
  val currentDir = os.Path(System.getProperty(Constants.SystemProps.UserDir))

  // Check if we're in a git repository
  if !GitAdapter.isGitRepository(currentDir) then
    Output.error("Not in a git repository. Please run 'git init' first.")
    System.exit(1)

  // Check if config already exists
  val configPath = currentDir / Constants.Paths.IwDir / "config.conf"
  if os.exists(configPath) && !force then
    Output.error(s"Configuration already exists at ${Constants.Paths.ConfigFile}")
    Output.info("Use 'iw init --force' to overwrite")
    System.exit(1)

  // Determine tracker type from flag or interactively
  val trackerType = trackerArg match
    case Some(Constants.TrackerTypeValues.Linear) => IssueTrackerType.Linear
    case Some(Constants.TrackerTypeValues.YouTrack) => IssueTrackerType.YouTrack
    case Some(Constants.TrackerTypeValues.GitHub) => IssueTrackerType.GitHub
    case Some(Constants.TrackerTypeValues.GitLab) => IssueTrackerType.GitLab
    case Some(invalid) =>
      Output.error(s"Invalid tracker type: $invalid. Use '${Constants.TrackerTypeValues.Linear}', '${Constants.TrackerTypeValues.YouTrack}', '${Constants.TrackerTypeValues.GitHub}', or '${Constants.TrackerTypeValues.GitLab}'.")
      sys.exit(1)
    case None =>
      // Interactive mode: detect from git remote or ask user
      val remote = GitAdapter.getRemoteUrl(currentDir)
      val suggestedTracker = remote.flatMap(TrackerDetector.suggestTracker)

      suggestedTracker match
        case Some(suggested) =>
          val trackerName = suggested match
            case IssueTrackerType.Linear => Constants.TrackerTypeValues.Linear
            case IssueTrackerType.YouTrack => Constants.TrackerTypeValues.YouTrack
            case IssueTrackerType.GitHub => Constants.TrackerTypeValues.GitHub
            case IssueTrackerType.GitLab => Constants.TrackerTypeValues.GitLab

          Output.info(s"Detected tracker: $trackerName (based on git remote)")
          val confirmed = Prompt.confirm(s"Use $trackerName?", default = true)

          if confirmed then suggested else askForTrackerType()
        case None =>
          Output.info("Could not detect tracker from git remote")
          askForTrackerType()

  // For GitHub, extract repository from git remote and get team prefix; for others, get team
  val (team, repository, teamPrefix, youtrackBaseUrl) = trackerType match
    case IssueTrackerType.GitHub =>
      // Use provided repository or auto-detect from git remote
      val ownerRepo = repositoryArg match
        case Some(repo) =>
          repo
        case None =>
          val remote = GitAdapter.getRemoteUrl(currentDir)
          val repo = remote.flatMap { r =>
            r.repositoryOwnerAndName match
              case Right(ownerRepo) => Some(ownerRepo)
              case Left(err) =>
                Output.warning(s"Could not auto-detect repository: $err")
                None
          }

          repo match
            case Some(ownerRepo) =>
              Output.info(s"Auto-detected repository: $ownerRepo")
              ownerRepo
            case None =>
              Prompt.ask("Enter GitHub repository (owner/repo format)")

      // Get team prefix for GitHub
      val prefix = teamPrefixArg match
        case Some(p) =>
          // Validate provided prefix
          TeamPrefixValidator.validate(p) match
            case Left(err) =>
              Output.error(s"Invalid team prefix: $err")
              sys.exit(1)
            case Right(validated) => validated
        case None =>
          // Suggest prefix from repository name
          val suggested = TeamPrefixValidator.suggestFromRepository(ownerRepo)
          Output.info(s"Suggested team prefix: $suggested")
          val input = Prompt.ask(s"Enter team prefix (2-10 uppercase letters) [$suggested]")
          val chosen = if input.trim.isEmpty then suggested else input.trim
          // Validate chosen prefix
          TeamPrefixValidator.validate(chosen) match
            case Left(err) =>
              Output.error(s"Invalid team prefix: $err")
              sys.exit(1)
            case Right(validated) => validated

      ("", Some(ownerRepo), Some(prefix), None)

    case IssueTrackerType.GitLab =>
      // Get remote URL for detection (needed for both repository and baseUrl)
      val remote = GitAdapter.getRemoteUrl(currentDir)

      // Use provided repository or auto-detect from git remote
      val ownerRepo = repositoryArg match
        case Some(repo) =>
          repo
        case None =>
          val repo = remote.flatMap { r =>
            r.extractGitLabRepository match
              case Right(ownerRepo) => Some(ownerRepo)
              case Left(err) =>
                Output.warning(s"Could not auto-detect repository: $err")
                None
          }

          repo match
            case Some(ownerRepo) =>
              Output.info(s"Auto-detected repository: $ownerRepo")
              ownerRepo
            case None =>
              Prompt.ask("Enter GitLab repository (owner/repo or group/subgroup/project format)")

      // Get team prefix for GitLab
      val prefix = teamPrefixArg match
        case Some(p) =>
          // Validate provided prefix
          TeamPrefixValidator.validate(p) match
            case Left(err) =>
              Output.error(s"Invalid team prefix: $err")
              sys.exit(1)
            case Right(validated) => validated
        case None =>
          // Suggest prefix from repository name (use last component for nested groups)
          val suggested = TeamPrefixValidator.suggestFromRepository(ownerRepo)
          Output.info(s"Suggested team prefix: $suggested")
          val input = Prompt.ask(s"Enter team prefix (2-10 uppercase letters) [$suggested]")
          val chosen = if input.trim.isEmpty then suggested else input.trim
          // Validate chosen prefix
          TeamPrefixValidator.validate(chosen) match
            case Left(err) =>
              Output.error(s"Invalid team prefix: $err")
              sys.exit(1)
            case Right(validated) => validated

      // Check if self-hosted GitLab
      val baseUrl = baseUrlArg match
        case Some(url) => Some(url)
        case None =>
          // Detect if self-hosted (not gitlab.com)
          remote.flatMap(_.host.toOption) match
            case Some(host) if host != "gitlab.com" =>
              Output.info(s"Detected self-hosted GitLab at $host")
              val url = Prompt.ask(s"Enter GitLab base URL (e.g., https://$host)")
              Some(url)
            case _ =>
              None // gitlab.com, no baseUrl needed

      ("", Some(ownerRepo), Some(prefix), baseUrl)

    case IssueTrackerType.YouTrack =>
      // YouTrack: get team and base URL
      val t = teamArg.getOrElse {
        Prompt.ask("Enter team/project identifier (e.g., MEDH, IWSD)")
      }
      val baseUrl = baseUrlArg.getOrElse {
        Prompt.ask("Enter YouTrack base URL (e.g., https://youtrack.example.com)")
      }
      (t, None, None, Some(baseUrl))

    case IssueTrackerType.Linear =>
      // Linear: get team only
      val t = teamArg.getOrElse {
        Prompt.ask("Enter team/project identifier (e.g., IWLE, TEST)")
      }
      (t, None, None, None)

  // Auto-detect project name from directory
  val projectName = currentDir.last

  // Create configuration
  val config = ProjectConfiguration.create(
    trackerType = trackerType,
    team = team,
    projectName = projectName,
    repository = repository,
    teamPrefix = teamPrefix,
    youtrackBaseUrl = youtrackBaseUrl
  )

  // Write configuration
  ConfigFileRepository.write(configPath, config)

  Output.success(s"Configuration created at ${Constants.Paths.ConfigFile}")
  Output.section("Next steps")

  // Display environment variable instructions
  trackerType match
    case IssueTrackerType.Linear =>
      Output.info("Set your API token:")
      Output.info(s"  export ${Constants.EnvVars.LinearApiToken}=lin_api_...")
    case IssueTrackerType.YouTrack =>
      Output.info("Set your API token:")
      Output.info(s"  export ${Constants.EnvVars.YouTrackApiToken}=perm:...")
    case IssueTrackerType.GitHub =>
      Output.info("GitHub tracker configured.")
      Output.info("Ensure the gh CLI is installed and authenticated:")
      Output.info("  gh auth login")
    case IssueTrackerType.GitLab =>
      Output.info("GitLab tracker configured.")
      Output.info("Ensure the glab CLI is installed and authenticated:")
      Output.info("  brew install glab  (macOS)")
      Output.info("  glab auth login")
      Output.info("")
      Output.info("For other platforms, see: https://gitlab.com/gitlab-org/cli")

  Output.info("")
  Output.info("Run './iw doctor' to verify your setup.")
