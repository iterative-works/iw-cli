// PURPOSE: Value object representing the result of worktree creation
// PURPOSE: Contains all information needed to show success state to user

package iw.core.dashboard.domain

case class WorktreeCreationResult(
  issueId: String,
  worktreePath: String,
  tmuxSessionName: String,
  tmuxAttachCommand: String
)
