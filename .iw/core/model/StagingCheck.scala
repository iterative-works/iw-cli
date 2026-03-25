// PURPOSE: Pure validation of worktree state before phase-commit
// PURPOSE: Checks that all changes are staged and no untracked/unstaged files remain

package iw.core.model

/** Snapshot of worktree state for pre-commit validation.
  *
  * @param stagedFiles Files in the staging area (ready to commit)
  * @param unstagedFiles Tracked files with unstaged modifications
  * @param untrackedFiles Files not tracked by git
  */
case class StagingCheck(
  stagedFiles: List[String],
  unstagedFiles: List[String],
  untrackedFiles: List[String]
):
  /** Validate that the worktree is ready for phase-commit.
    *
    * Requires: staged changes exist, no unstaged modifications, no untracked files.
    * Returns Right(()) if ready, Left with a descriptive error message if not.
    */
  def readyToCommit: Either[String, Unit] =
    val problems = List.newBuilder[String]

    if stagedFiles.isEmpty then
      problems += "No staged changes found. Stage your changes with `git add` before running phase-commit."

    if unstagedFiles.nonEmpty then
      problems += s"Unstaged modifications:\n${unstagedFiles.map(f => s"  $f").mkString("\n")}\nStage them with `git add <file>` or discard with `git checkout -- <file>`."

    if untrackedFiles.nonEmpty then
      problems += s"Untracked files:\n${untrackedFiles.map(f => s"  $f").mkString("\n")}\nStage with `git add`, add to .gitignore, or remove them."

    val result = problems.result()
    if result.isEmpty then Right(())
    else Left(result.mkString("\n\n"))

object StagingCheck:

  /** Parse `git status --porcelain` output into a StagingCheck.
    *
    * Porcelain format: two-character status prefix, a space, then the file path.
    * First character = staged status, second character = unstaged status.
    * `??` = untracked, `!!` = ignored.
    */
  def fromPorcelain(output: String): StagingCheck =
    val lines = output.split("\n").toList.filter(_.nonEmpty)

    val staged = List.newBuilder[String]
    val unstaged = List.newBuilder[String]
    val untracked = List.newBuilder[String]

    lines.foreach { line =>
      if line.length >= 3 then
        val x = line(0) // staged status
        val y = line(1) // unstaged status
        val file = line.drop(3)

        if x == '?' && y == '?' then
          untracked += file
        else if x == '!' && y == '!' then
          () // ignored, skip
        else
          if x != ' ' && x != '?' then staged += file
          if y != ' ' && y != '?' then unstaged += file
    }

    StagingCheck(staged.result(), unstaged.result(), untracked.result())
