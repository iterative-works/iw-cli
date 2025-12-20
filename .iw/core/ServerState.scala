// PURPOSE: Domain model representing the server's state including all registered worktrees
// PURPOSE: Provides pure functions for listing worktrees sorted by activity

package iw.core.domain

case class ServerState(
  worktrees: Map[String, WorktreeRegistration],
  issueCache: Map[String, CachedIssue] = Map.empty
):
  def listByActivity: List[WorktreeRegistration] =
    worktrees.values.toList.sortBy(_.lastSeenAt.getEpochSecond)(Ordering[Long].reverse)
