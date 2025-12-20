// PURPOSE: Pure functions for unregistering worktrees from server state
// PURPOSE: Provides unregister and pruneNonExistent operations for worktree cleanup

package iw.core.application

import iw.core.domain.ServerState

object WorktreeUnregistrationService:
  /** Remove a worktree from state.
    *
    * @param state Current server state
    * @param issueId Issue ID of the worktree to remove
    * @return Right with updated state if worktree exists, Left with error message if not found
    */
  def unregister(state: ServerState, issueId: String): Either[String, ServerState] =
    if state.worktrees.contains(issueId) then
      Right(state.removeWorktree(issueId))
    else
      Left(s"Worktree not found: $issueId")

  /** Prune worktrees whose paths no longer exist.
    *
    * @param state Current server state
    * @param pathExists Function to check if a path exists on filesystem
    * @return Updated state with non-existent worktrees and their caches removed
    */
  def pruneNonExistent(
    state: ServerState,
    pathExists: String => Boolean
  ): ServerState =
    val validWorktrees = state.worktrees.filter { (_, wt) =>
      pathExists(wt.path)
    }
    val removedIds = state.worktrees.keySet -- validWorktrees.keySet

    removedIds.foldLeft(state.copy(worktrees = validWorktrees)) { (s, id) =>
      s.copy(
        issueCache = s.issueCache - id,
        progressCache = s.progressCache - id,
        prCache = s.prCache - id
      )
    }
