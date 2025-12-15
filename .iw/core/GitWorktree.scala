// PURPOSE: Git worktree adapter for creating and managing worktrees
// PURPOSE: Provides worktree operations including creation, existence checks, and branch handling

package iw.core

import java.nio.file.Path

object GitWorktreeAdapter:
  /** Check if a worktree exists for the given path */
  def worktreeExists(path: Path, workDir: Path): Boolean =
    val result = ProcessAdapter.run(Seq("git", "-C", workDir.toString, "worktree", "list", "--porcelain"))
    result.stdout.contains(s"worktree ${path.toAbsolutePath}")

  /** Check if a branch exists */
  def branchExists(branchName: String, workDir: Path): Boolean =
    val result = ProcessAdapter.run(
      Seq("git", "-C", workDir.toString, "show-ref", "--verify", "--quiet", s"refs/heads/$branchName")
    )
    result.exitCode == 0

  /** Create a new worktree with a new branch */
  def createWorktree(path: Path, branchName: String, workDir: Path): Either[String, Unit] =
    val result = ProcessAdapter.run(
      Seq("git", "-C", workDir.toString, "worktree", "add", "-b", branchName, path.toString)
    )
    if result.exitCode == 0 then Right(())
    else Left(s"Failed to create worktree: ${result.stderr}")

  /** Create worktree for existing branch */
  def createWorktreeForBranch(path: Path, branchName: String, workDir: Path): Either[String, Unit] =
    val result = ProcessAdapter.run(
      Seq("git", "-C", workDir.toString, "worktree", "add", path.toString, branchName)
    )
    if result.exitCode == 0 then Right(())
    else Left(s"Failed to create worktree: ${result.stderr}")

  /** Remove an existing worktree */
  def removeWorktree(path: Path, workDir: Path, force: Boolean): Either[String, Unit] =
    val args = if force then
      Seq("git", "-C", workDir.toString, "worktree", "remove", "--force", path.toString)
    else
      Seq("git", "-C", workDir.toString, "worktree", "remove", path.toString)

    val result = ProcessAdapter.run(args)
    if result.exitCode == 0 then Right(())
    else Left(s"Failed to remove worktree: ${result.stderr}")
