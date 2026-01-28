// PURPOSE: Domain model representing the server's state including all registered worktrees
// PURPOSE: Provides pure functions for listing worktrees sorted by issue ID

package iw.core.model

case class ServerState(
  worktrees: Map[String, WorktreeRegistration],
  issueCache: Map[String, CachedIssue] = Map.empty,
  progressCache: Map[String, CachedProgress] = Map.empty,
  prCache: Map[String, CachedPR] = Map.empty,
  reviewStateCache: Map[String, CachedReviewState] = Map.empty
):
  def listByIssueId: List[WorktreeRegistration] =
    worktrees.values.toList.sortBy(_.issueId)

  def removeWorktree(issueId: String): ServerState =
    copy(
      worktrees = worktrees - issueId,
      issueCache = issueCache - issueId,
      progressCache = progressCache - issueId,
      prCache = prCache - issueId,
      reviewStateCache = reviewStateCache - issueId
    )
