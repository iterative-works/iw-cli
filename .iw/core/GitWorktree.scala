// PURPOSE: Git worktree adapter for creating and managing worktrees
// PURPOSE: Provides worktree operations including creation, existence checks, and branch handling

//> using scala 3.3.1
//> using file "Process.scala"

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
