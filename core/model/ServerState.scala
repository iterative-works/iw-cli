// PURPOSE: Domain model representing the server's state including all registered worktrees and projects
// PURPOSE: Provides pure functions for listing worktrees sorted by issue ID and projects sorted by name

package iw.core.model

case class ServerState(
    worktrees: Map[String, WorktreeRegistration],
    issueCache: Map[String, CachedIssue] = Map.empty,
    progressCache: Map[String, CachedProgress] = Map.empty,
    prCache: Map[String, CachedPR] = Map.empty,
    reviewStateCache: Map[String, CachedReviewState] = Map.empty,
    projects: Map[String, ProjectRegistration] = Map.empty
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

  def listProjects: List[ProjectRegistration] =
    projects.values.toList.sortBy(_.projectName)

  def removeProject(path: String): ServerState =
    copy(projects = projects - path)
