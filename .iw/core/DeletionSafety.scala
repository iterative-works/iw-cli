// PURPOSE: Value object representing safety checks for worktree deletion
// PURPOSE: Encapsulates conditions that prevent safe deletion of a worktree

package iw.core

case class DeletionSafety(
  hasUncommittedChanges: Boolean,
  isActiveSession: Boolean
)

object DeletionSafety:
  def isSafe(safety: DeletionSafety): Boolean =
    !safety.hasUncommittedChanges && !safety.isActiveSession
