// PURPOSE: Pure domain type for a forge pull request returned from creation
// PURPOSE: Carries number, HTML URL, and head commit SHA for use in merge and CI-status operations

package iw.core.model

/** A pull request as returned by the forge PR-creation endpoint.
  *
  * @param number
  *   The PR's numeric index within the repository (used as the merge key on
  *   Forgejo)
  * @param htmlUrl
  *   The human-readable URL for the pull request
  * @param headSha
  *   The commit SHA at the head of the PR branch (used for CI status polling)
  */
case class ForgePullRequest(number: Int, htmlUrl: String, headSha: String)
