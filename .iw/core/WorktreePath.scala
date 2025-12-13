// PURPOSE: Worktree path value object for calculating sibling directory names
// PURPOSE: Provides directory naming and path resolution for issue worktrees

//> using scala 3.3.1
//> using file "IssueId.scala"

package iw.core

import java.nio.file.Path

case class WorktreePath(projectName: String, issueId: IssueId):
  def directoryName: String = s"$projectName-${issueId.value}"

  /** Resolve to actual path as sibling of current directory */
  def resolve(currentDir: Path): Path =
    currentDir.getParent.resolve(directoryName)

  def sessionName: String = directoryName
