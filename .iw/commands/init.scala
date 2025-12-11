// PURPOSE: Initialize iw-cli configuration for the project
// USAGE: iw init [--force]
// ARGS:
//   --force: Overwrite existing configuration
// EXAMPLE: iw init

//> using scala 3.3.1
//> using dep com.typesafe:config:1.4.3
//> using file "../core/Output.scala"
//> using file "../core/Config.scala"
//> using file "../core/ConfigRepository.scala"
//> using file "../core/Git.scala"
//> using file "../core/Prompt.scala"

import iw.core.*
import java.nio.file.{Paths, Files}

object InitCommand:
  def main(args: Array[String]): Unit =
    val force = args.contains("--force")
    val currentDir = Paths.get(System.getProperty("user.dir"))

    // Check if we're in a git repository
    if !GitAdapter.isGitRepository(currentDir) then
      Output.error("Not in a git repository. Please run 'git init' first.")
      System.exit(1)

    // Check if config already exists
    val configPath = currentDir.resolve(".iw").resolve("config.conf")
    if Files.exists(configPath) && !force then
      Output.error("Configuration already exists at .iw/config.conf")
      Output.info("Use 'iw init --force' to overwrite")
      System.exit(1)

    // Get git remote and suggest tracker
    val remote = GitAdapter.getRemoteUrl(currentDir)
    val suggestedTracker = remote.flatMap(TrackerDetector.suggestTracker)

    // Ask user for tracker type
    val trackerType = suggestedTracker match
      case Some(suggested) =>
        val trackerName = suggested match
          case IssueTrackerType.Linear => "linear"
          case IssueTrackerType.YouTrack => "youtrack"

        Output.info(s"Detected tracker: $trackerName (based on git remote)")
        val confirmed = Prompt.confirm(s"Use $trackerName?", default = true)

        if confirmed then
          suggested
        else
          askForTrackerType()
      case None =>
        Output.info("Could not detect tracker from git remote")
        askForTrackerType()

    // Ask for team/project identifier
    val team = Prompt.ask("Enter team/project identifier (e.g., IWLE, TEST)")

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

    Output.success("Configuration created at .iw/config.conf")
    Output.section("Next steps")

    // Display environment variable instructions
    trackerType match
      case IssueTrackerType.Linear =>
        Output.info("Set your API token:")
        Output.info("  export LINEAR_API_TOKEN=lin_api_...")
      case IssueTrackerType.YouTrack =>
        Output.info("Set your API token:")
        Output.info("  export YOUTRACK_API_TOKEN=perm:...")

    Output.info("")
    Output.info("Run './iw doctor' to verify your setup.")

  private def askForTrackerType(): IssueTrackerType =
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
