// PURPOSE: Application service for deriving main projects from registered worktrees
// PURPOSE: Handles project path derivation, config loading, and deduplication logic

package iw.dashboard.application

import iw.core.model.{WorktreeRegistration, ProjectRegistration}
import iw.dashboard.domain.MainProject
import iw.core.adapters.ConfigFileRepository
import iw.core.model.{ProjectConfiguration, Constants, TrackerUrlBuilder}

object MainProjectService:
  /** Filter worktrees by project name.
    *
    * Uses MainProject.deriveMainProjectPath to extract the main project path
    * from each worktree path, then compares the last path component (project
    * name) to the requested project name.
    *
    * @param worktrees
    *   List of registered worktrees
    * @param projectName
    *   Project name to filter by (exact match, case-sensitive)
    * @return
    *   List of worktrees belonging to the specified project
    */
  def filterByProjectName(
      worktrees: List[WorktreeRegistration],
      projectName: String
  ): List[WorktreeRegistration] =
    worktrees.filter { wt =>
      MainProject
        .deriveMainProjectPath(wt.path)
        .exists { mainProjectPath =>
          val pathComponents = mainProjectPath.split('/')
          pathComponents.lastOption.contains(projectName)
        }
    }

  /** Derive unique main projects from a list of registered worktrees.
    *
    * This function:
    *   1. Extracts main project paths from worktree paths using pattern
    *      matching
    *   2. Loads config for each unique main project path
    *   3. Creates MainProject instances with metadata from config
    *   4. Deduplicates projects by path
    *   5. Filters out projects where config loading fails
    *
    * @param worktrees
    *   List of registered worktrees
    * @param loadConfig
    *   Function to load project configuration from a path
    * @return
    *   List of unique MainProject instances
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
            val team = config.teamIdentifier

            // Build tracker URL based on tracker type
            val trackerUrl = TrackerUrlBuilder.buildTrackerUrl(config)

            Some(
              MainProject(
                path = mainProjectPath,
                projectName = projectName,
                trackerType = trackerTypeStr,
                team = team,
                trackerUrl = trackerUrl
              )
            )

          case Left(_) =>
            // Config loading failed - filter out this project
            None
      }
      .toList

  /** Merge registered projects and worktree-derived projects into a unified
    * list.
    *
    * Produces a list of MainProject instances from both sources, deduplicating
    * by path. When both a registered project and a derived project share a
    * path, the derived project takes precedence since its metadata comes from
    * fresher on-disk config.
    *
    * @param worktrees
    *   List of registered worktrees
    * @param registeredProjects
    *   Map from path to registered project
    * @param loadConfig
    *   Function to load project configuration from a path
    * @return
    *   Merged list of unique MainProject instances
    */
  def resolveProjects(
      worktrees: List[WorktreeRegistration],
      registeredProjects: Map[String, ProjectRegistration],
      loadConfig: os.Path => Either[String, ProjectConfiguration]
  ): List[MainProject] =
    val derived = deriveFromWorktrees(worktrees, loadConfig)

    val fromRegistered = registeredProjects.values.map { reg =>
      MainProject(
        path = os.Path(reg.path),
        projectName = reg.projectName,
        trackerType = reg.trackerType,
        team = reg.team,
        trackerUrl = reg.trackerUrl
      )
    }

    // Build map of registered projects, then overlay derived ones (derived wins on collision)
    val registeredByPath = fromRegistered.map(p => p.path -> p).toMap
    val derivedByPath = derived.map(p => p.path -> p).toMap
    (registeredByPath ++ derivedByPath).values.toList

  /** Load project configuration from a main project path.
    *
    * Reads `.iw/config.conf` from the specified project directory.
    *
    * @param mainProjectPath
    *   Path to the main project directory
    * @return
    *   Either error message or ProjectConfiguration
    */
  def loadConfig(
      mainProjectPath: os.Path
  ): Either[String, ProjectConfiguration] =
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
