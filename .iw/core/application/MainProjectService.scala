// PURPOSE: Application service for deriving main projects from registered worktrees
// PURPOSE: Handles project path derivation, config loading, and deduplication logic

package iw.core.application

import iw.core.domain.{WorktreeRegistration, MainProject}
import iw.core.{ProjectConfiguration, ConfigFileRepository, Constants}

object MainProjectService:
  /** Derive unique main projects from a list of registered worktrees.
    *
    * This function:
    * 1. Extracts main project paths from worktree paths using pattern matching
    * 2. Loads config for each unique main project path
    * 3. Creates MainProject instances with metadata from config
    * 4. Deduplicates projects by path
    * 5. Filters out projects where config loading fails
    *
    * @param worktrees List of registered worktrees
    * @param loadConfig Function to load project configuration from a path
    * @return List of unique MainProject instances
    */
  def deriveFromWorktrees(
    worktrees: List[WorktreeRegistration],
    loadConfig: os.Path => Either[String, ProjectConfiguration]
  ): List[MainProject] =
    worktrees
      // Step 1: Extract main project paths from worktree paths
      .flatMap { worktree =>
        MainProject.deriveMainProjectPath(worktree.path).map { pathStr =>
          (os.Path(pathStr), worktree)
        }
      }
      // Step 2: Group by main project path to deduplicate
      .groupBy(_._1)
      // Step 3: For each unique path, load config and create MainProject
      .flatMap { case (mainProjectPath, worktreesForProject) =>
        loadConfig(mainProjectPath) match
          case Right(config) =>
            // Extract project name from path (last component)
            val projectName = mainProjectPath.last

            // Create MainProject with metadata from config
            val trackerTypeStr = config.trackerType.toString.toLowerCase
            val team = config.trackerType match
              case iw.core.IssueTrackerType.GitHub =>
                config.repository.getOrElse(config.team)
              case _ =>
                config.team

            // Build tracker URL based on tracker type
            val trackerUrl = buildTrackerUrl(config)

            Some(MainProject(
              path = mainProjectPath,
              projectName = projectName,
              trackerType = trackerTypeStr,
              team = team,
              trackerUrl = trackerUrl
            ))

          case Left(_) =>
            // Config loading failed - filter out this project
            None
      }
      .toList

  /** Build the issue tracker URL for a project.
    *
    * @param config Project configuration
    * @return Optional tracker URL (None if not enough info)
    */
  private def buildTrackerUrl(config: ProjectConfiguration): Option[String] =
    config.trackerType match
      case iw.core.IssueTrackerType.GitHub =>
        config.repository.map(repo => s"https://github.com/$repo/issues")
      case iw.core.IssueTrackerType.Linear =>
        Some(s"https://linear.app/${config.team.toLowerCase}")
      case iw.core.IssueTrackerType.YouTrack =>
        config.youtrackBaseUrl.map(baseUrl =>
          s"${baseUrl.stripSuffix("/")}/issues/${config.team}"
        )

  /** Load project configuration from a main project path.
    *
    * Reads `.iw/config.conf` from the specified project directory.
    *
    * @param mainProjectPath Path to the main project directory
    * @return Either error message or ProjectConfiguration
    */
  def loadConfig(mainProjectPath: os.Path): Either[String, ProjectConfiguration] =
    val configPath = mainProjectPath / ".iw" / "config.conf"

    // Check if directory exists
    if !os.exists(mainProjectPath) then
      Left(s"Project directory does not exist: $mainProjectPath")
    else if !os.isDir(mainProjectPath) then
      Left(s"Path is not a directory: $mainProjectPath")
    else
      // Try to read config
      ConfigFileRepository.read(configPath) match
        case Some(config) =>
          Right(config)
        case None =>
          Left(s"Config file not found or invalid: $configPath")
