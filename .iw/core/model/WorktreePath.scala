// PURPOSE: Worktree path value object for calculating sibling directory names
// PURPOSE: Provides directory naming and path resolution for issue worktrees

package iw.core.model

case class WorktreePath(projectName: String, issueId: IssueId):
  def directoryName: String = s"$projectName-${issueId.value}"

  /** Resolve to actual path as sibling of current directory */
  def resolve(currentDir: os.Path): os.Path =
    currentDir / os.up / directoryName

  def sessionName: String = directoryName
