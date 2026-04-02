// PURPOSE: Domain model representing a main project directory from which worktrees are created
// PURPOSE: Contains project metadata including path, name, tracker type, and team identifier

package iw.core.dashboard.domain

import iw.core.model.ProjectPath

case class MainProject(
    path: os.Path,
    projectName: String,
    trackerType: String,
    team: String,
    trackerUrl: Option[String] = None
)

object MainProject:
  /** Derive main project path from a worktree path by stripping the issue ID
    * suffix.
    *
    * Worktree paths follow the pattern: {mainProjectPath}-{issueId} where
    * issueId matches the pattern: [A-Z]+-[0-9]+ or just [0-9]+
    *
    * Examples:
    *   - /home/user/projects/iw-cli-IW-79 → Some(/home/user/projects/iw-cli)
    *   - /home/user/projects/kanon-IWLE-123 → Some(/home/user/projects/kanon)
    *   - /opt/code/myproject-123 → Some(/opt/code/myproject)
    *   - /home/user/projects/just-a-directory → None (no issue ID pattern)
    *
    * @param worktreePath
    *   The full path to the worktree directory
    * @return
    *   Some(mainProjectPath) if pattern matches, None otherwise
    */
  def deriveMainProjectPath(worktreePath: String): Option[String] =
    ProjectPath.deriveMainProjectPath(worktreePath)
