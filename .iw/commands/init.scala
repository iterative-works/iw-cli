// PURPOSE: Initialize iw-cli configuration for the project
// USAGE: iw init [--force] [--tracker=linear|youtrack] [--team=TEAM]
// ARGS:
//   --force: Overwrite existing configuration
//   --tracker=linear|youtrack: Set tracker type (skips prompt)
//   --team=TEAM: Set team identifier (skips prompt)
// EXAMPLE: iw init
// EXAMPLE: iw init --tracker=linear --team=IWLE

import iw.core.*
import java.nio.file.{Paths, Files}

def parseArg(args: Seq[String], prefix: String): Option[String] =
  args.find(_.startsWith(prefix)).map(_.drop(prefix.length))

def askForTrackerType(): IssueTrackerType =
  Output.info("Available trackers:")
  Output.info("  1. Linear")
  Output.info("  2. YouTrack")

  val choice = Prompt.ask("Select tracker (1 or 2)")

  choice match
    case "1" | "linear" => IssueTrackerType.Linear
    case "2" | "youtrack" => IssueTrackerType.YouTrack
    case _ =>
      Output.error("Invalid choice. Please select 1 or 2.")
      askForTrackerType()

@main def init(args: String*): Unit =
  val force = args.contains("--force")
  val trackerArg = parseArg(args, "--tracker=")
  val teamArg = parseArg(args, "--team=")
  val currentDir = Paths.get(System.getProperty(Constants.SystemProps.UserDir))

  // Check if we're in a git repository
  if !GitAdapter.isGitRepository(currentDir) then
    Output.error("Not in a git repository. Please run 'git init' first.")
    System.exit(1)

  // Check if config already exists
  val configPath = currentDir.resolve(Constants.Paths.IwDir).resolve("config.conf")
  if Files.exists(configPath) && !force then
    Output.error(s"Configuration already exists at ${Constants.Paths.ConfigFile}")
    Output.info("Use 'iw init --force' to overwrite")
    System.exit(1)

  // Determine tracker type from flag or interactively
  val trackerType = trackerArg match
    case Some(Constants.TrackerTypeValues.Linear) => IssueTrackerType.Linear
    case Some(Constants.TrackerTypeValues.YouTrack) => IssueTrackerType.YouTrack
    case Some(invalid) =>
      Output.error(s"Invalid tracker type: $invalid. Use '${Constants.TrackerTypeValues.Linear}' or '${Constants.TrackerTypeValues.YouTrack}'.")
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

          Output.info(s"Detected tracker: $trackerName (based on git remote)")
          val confirmed = Prompt.confirm(s"Use $trackerName?", default = true)

          if confirmed then suggested else askForTrackerType()
        case None =>
          Output.info("Could not detect tracker from git remote")
          askForTrackerType()

  // Get team from flag or interactively
  val team = teamArg.getOrElse {
    Prompt.ask("Enter team/project identifier (e.g., IWLE, TEST)")
  }

  // Auto-detect project name from directory
  val projectName = currentDir.getFileName.toString

  // Create configuration
  val config = ProjectConfiguration(
    trackerType = trackerType,
    team = team,
    projectName = projectName
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

  Output.info("")
  Output.info("Run './iw doctor' to verify your setup.")
