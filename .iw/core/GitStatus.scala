// PURPOSE: Domain model for git repository status
// PURPOSE: Represents branch name and working directory state (clean/dirty)

package iw.core.domain

/** Git repository status for a worktree.
  *
  * @param branchName Current branch name (e.g., "main", "feature/IWLE-123")
  * @param isClean True if no uncommitted changes, false if dirty working tree
  */
case class GitStatus(
  branchName: String,
  isClean: Boolean
):
  /** Status indicator text for display.
    * Returns "✓ clean" for clean, "⚠ uncommitted" for dirty.
    */
  def statusIndicator: String =
    if isClean then "✓ clean" else "⚠ uncommitted"

  /** CSS class for status styling.
    * Returns "git-clean" for clean, "git-dirty" for dirty.
    */
  def statusCssClass: String =
    if isClean then "git-clean" else "git-dirty"
