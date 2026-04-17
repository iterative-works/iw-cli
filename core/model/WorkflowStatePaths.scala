// PURPOSE: Distinguishes workflow-owned state paths from user code when checking for uncommitted changes
// PURPOSE: Lets batch-implement re-enter after an interrupted run without being blocked by its own state writes

package iw.core.model

/** Pure helper for classifying dirty working-tree paths against the issue's
  * workflow state directory.
  */
object WorkflowStatePaths:

  /** Directory (relative to repo root) where the workflow writes its state
    * files for a given issue.
    */
  def stateDir(issueId: String): String =
    s"project-management/issues/$issueId/"

  /** Split a list of repo-relative paths into (stateOwned, userOwned).
    *
    * Paths under `project-management/issues/<issueId>/` are considered
    * state-owned — the workflow writes them (review-state.json, phase files,
    * etc.) and has permission to auto-commit them. Everything else is user code
    * that must be committed manually before the workflow runs.
    */
  def partition(
      paths: List[String],
      issueId: String
  ): (List[String], List[String]) =
    val prefix = stateDir(issueId)
    paths.partition(_.startsWith(prefix))
