// PURPOSE: Value object for CLI worktrees command output
// PURPOSE: Contains worktree metadata with cached issue and PR data for list display

package iw.core.model

import upickle.default.*

/** Summary of a worktree for `iw worktrees` output.
  *
  * @param issueId Issue identifier (e.g., "IWLE-123")
  * @param path Absolute path to worktree directory
  * @param issueTitle Issue title from cache, if available
  * @param issueStatus Issue status from cache (e.g., "In Progress"), if available
  * @param prState PR state from cache (e.g., "Open", "Merged"), if available
  * @param reviewDisplay Review state display text from cache, if available
  * @param needsAttention True if review state indicates human attention needed
  */
case class WorktreeSummary(
  issueId: String,
  path: String,
  issueTitle: Option[String],
  issueStatus: Option[String],
  prState: Option[String],
  reviewDisplay: Option[String],
  needsAttention: Boolean
) derives ReadWriter
