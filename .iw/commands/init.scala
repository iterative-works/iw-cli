// PURPOSE: Initialize iw-cli configuration for the project
// USAGE: iw init [--force] [--tracker=linear|youtrack|github] [--team=TEAM] [--team-prefix=PREFIX] [--base-url=URL]
// ARGS:
//   --force: Overwrite existing configuration
//   --tracker=linear|youtrack|github: Set tracker type (skips prompt)
//   --team=TEAM: Set team identifier for Linear/YouTrack (skips prompt)
//   --team-prefix=PREFIX: Set team prefix for GitHub (skips prompt)
//   --base-url=URL: Set YouTrack base URL (skips prompt)
// EXAMPLE: iw init
// EXAMPLE: iw init --tracker=linear --team=IWLE
// EXAMPLE: iw init --tracker=github --team-prefix=IWCLI
// EXAMPLE: iw init --tracker=youtrack --team=MEDH --base-url=https://youtrack.example.com

import iw.core.*

def parseArg(args: Seq[String], prefix: String): Option[String] =
  args.find(_.startsWith(prefix)).map(_.drop(prefix.length))

def askForTrackerType(): IssueTrackerType =
  Output.info("Available trackers:")
  Output.info("  1. Linear")
  Output.info("  2. YouTrack")
  Output.info("  3. GitHub")

  val choice = Prompt.ask("Select tracker (1, 2, or 3)")

  choice match
    case "1" | "linear" => IssueTrackerType.Linear
    case "2" | "youtrack" => IssueTrackerType.YouTrack
    case "3" | "github" => IssueTrackerType.GitHub
    case _ =>
      Output.error("Invalid choice. Please select 1, 2, or 3.")
      askForTrackerType()

@main def init(args: String*): Unit =
  val force = args.contains("--force")
  val trackerArg = parseArg(args, "--tracker=")
  val teamArg = parseArg(args, "--team=")
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
    case Some(invalid) =>
      Output.error(s"Invalid tracker type: $invalid. Use '${Constants.TrackerTypeValues.Linear}', '${Constants.TrackerTypeValues.YouTrack}', or '${Constants.TrackerTypeValues.GitHub}'.")
      System.exit(1)
      throw RuntimeException("unreachable") // for type checker
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

          Output.info(s"Detected tracker: $trackerName (based on git remote)")
          val confirmed = Prompt.confirm(s"Use $trackerName?", default = true)

          if confirmed then suggested else askForTrackerType()
        case None =>
          Output.info("Could not detect tracker from git remote")
          askForTrackerType()

  // For GitHub, extract repository from git remote and get team prefix; for others, get team
  val (team, repository, teamPrefix, youtrackBaseUrl) = trackerType match
    case IssueTrackerType.GitHub =>
      // Extract repository from git remote
      val remote = GitAdapter.getRemoteUrl(currentDir)
      val repo = remote.flatMap { r =>
        r.repositoryOwnerAndName match
          case Right(ownerRepo) => Some(ownerRepo)
          case Left(err) =>
            Output.warning(s"Could not auto-detect repository: $err")
            None
      }

      val ownerRepo = repo match
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
              System.exit(1)
              throw RuntimeException("unreachable") // for type checker
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
              System.exit(1)
              throw RuntimeException("unreachable") // for type checker
            case Right(validated) => validated

      ("", Some(ownerRepo), Some(prefix), None)

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
  val config = ProjectConfiguration(
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

  Output.info("")
  Output.info("Run './iw doctor' to verify your setup.")
