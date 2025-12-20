// PURPOSE: Application service for git status detection
// PURPOSE: Pure business logic with injected command execution

package iw.core.application

import iw.core.domain.GitStatus

/** Git status detection service.
  * Pure application logic - receives command execution function from caller.
  * Follows FCIS pattern: no side effects in service, effects injected.
  */
object GitStatusService:

  /** Get git status for worktree.
    *
    * Executes git commands via injected execCommand function.
    * Returns GitStatus with branch name and clean/dirty state.
    *
    * @param worktreePath Path to worktree directory
    * @param execCommand Command execution function (command, args) => Either[error, stdout]
    * @return Right(GitStatus) if success, Left(error message) if git unavailable
    */
  def getGitStatus(
    worktreePath: String,
    execCommand: (String, Array[String]) => Either[String, String]
  ): Either[String, GitStatus] =
    for
      branchOutput <- execCommand("git", Array("-C", worktreePath, "rev-parse", "--abbrev-ref", "HEAD"))
      statusOutput <- execCommand("git", Array("-C", worktreePath, "status", "--porcelain"))
    yield
      val branchName = parseBranchName(branchOutput).getOrElse("unknown")
      val isClean = isWorkingTreeClean(statusOutput)
      GitStatus(branchName, isClean)

  /** Parse branch name from git output.
    *
    * Handles: normal branch, detached HEAD, errors.
    *
    * @param output Git rev-parse output (e.g., "main\n")
    * @return Some(branch name) if found, None if empty
    */
  def parseBranchName(output: String): Option[String] =
    val trimmed = output.trim
    if trimmed.isEmpty then None
    else Some(trimmed)

  /** Parse working tree status from git status --porcelain output.
    *
    * Returns true if clean (empty output), false if dirty.
    * Dirty = uncommitted changes OR staged changes.
    *
    * @param output Git status --porcelain output
    * @return true if working tree is clean, false if dirty
    */
  def isWorkingTreeClean(output: String): Boolean =
    output.trim.isEmpty
