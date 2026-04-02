// PURPOSE: Value object for CLI status command output
// PURPOSE: Contains comprehensive worktree status with git, issue, PR, and workflow data

package iw.core.model

import upickle.default.*

/** Detailed status of a single worktree for `iw status` output.
  *
  * @param issueId
  *   Issue identifier (e.g., "IWLE-123")
  * @param path
  *   Absolute path to worktree directory
  * @param branchName
  *   Current git branch name
  * @param gitClean
  *   True if no uncommitted changes
  * @param issueTitle
  *   Issue title from cache, if available
  * @param issueStatus
  *   Issue status from cache, if available
  * @param issueUrl
  *   Direct link to issue in tracker, if available
  * @param prUrl
  *   PR URL, if available
  * @param prState
  *   PR state as string (e.g., "Open"), if available
  * @param prNumber
  *   PR number, if available
  * @param reviewDisplay
  *   Review state display text, if available
  * @param reviewBadges
  *   Review state badges as list of label strings, if available
  * @param needsAttention
  *   True if review state indicates human attention needed
  * @param currentPhase
  *   Current workflow phase number, if available
  * @param totalPhases
  *   Total workflow phases, if available
  * @param overallProgress
  *   Overall task completion percentage (0-100), if available
  */
case class WorktreeStatus(
    issueId: String,
    path: String,
    branchName: Option[String],
    gitClean: Option[Boolean],
    issueTitle: Option[String],
    issueStatus: Option[String],
    issueUrl: Option[String],
    prUrl: Option[String],
    prState: Option[String],
    prNumber: Option[Int],
    reviewDisplay: Option[String],
    reviewBadges: Option[List[String]],
    needsAttention: Boolean,
    currentPhase: Option[Int],
    totalPhases: Option[Int],
    overallProgress: Option[Int]
) derives ReadWriter
