// PURPOSE: Value object for CLI worktrees command output
// PURPOSE: Contains worktree metadata with cached issue, PR, and workflow data for list display

package iw.core.model

import upickle.default.*

/** Summary of a worktree for `iw worktrees` output.
  *
  * @param issueId Issue identifier (e.g., "IWLE-123")
  * @param path Absolute path to worktree directory
  * @param issueTitle Issue title from cache, if available
  * @param issueStatus Issue status from cache (e.g., "In Progress"), if available
  * @param issueUrl Direct link to issue in tracker, if available
  * @param prUrl Direct link to pull request, if available
  * @param prState PR state from cache (e.g., "Open", "Merged"), if available
  * @param activity Current activity state ("working" | "waiting"), if available
  * @param workflowType Workflow methodology ("agile" | "waterfall" | "diagnostic"), if available
  * @param workflowDisplay Workflow state display text from cache, if available
  * @param needsAttention True if review state indicates human attention needed
  * @param currentPhase Current phase number (1-based), if available
  * @param totalPhases Total number of phases in workflow, if available
  * @param completedTasks Completed task count across all phases, if available
  * @param totalTasks Total task count across all phases, if available
  * @param registeredAt ISO timestamp when worktree was registered, if available
  * @param lastActivityAt ISO timestamp of last seen activity, if available
  */
case class WorktreeSummary(
  // Identity
  issueId: String,
  path: String,
  // Issue metadata (from issueCache)
  issueTitle: Option[String],
  issueStatus: Option[String],
  // URLs
  issueUrl: Option[String],
  prUrl: Option[String],
  // PR state (from prCache)
  prState: Option[String],
  // Workflow state (from reviewStateCache)
  activity: Option[String],
  workflowType: Option[String],
  workflowDisplay: Option[String],
  needsAttention: Boolean,
  // Progress (from progressCache)
  currentPhase: Option[Int],
  totalPhases: Option[Int],
  completedTasks: Option[Int],
  totalTasks: Option[Int],
  // Timestamps (from WorktreeRegistration)
  registeredAt: Option[String],
  lastActivityAt: Option[String]
) derives ReadWriter
