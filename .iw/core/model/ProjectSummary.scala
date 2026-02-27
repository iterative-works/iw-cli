// PURPOSE: Value object for CLI projects command output
// PURPOSE: Contains aggregated project metadata for displaying project overview

package iw.core.model

import upickle.default.*

/** Summary of a main project for `iw projects` output.
  *
  * @param name Project name (e.g., "iw-cli", "kanon")
  * @param path Absolute path to main project directory
  * @param trackerType Tracker type (e.g., "linear", "github")
  * @param team Team identifier (e.g., "IWLE", "iterative-works/iw-cli")
  * @param worktreeCount Number of active worktrees for this project
  */
case class ProjectSummary(
  name: String,
  path: String,
  trackerType: String,
  team: String,
  worktreeCount: Int
) derives ReadWriter
