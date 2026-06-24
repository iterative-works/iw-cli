// PURPOSE: Pure URL parsing utilities for Forgejo pull request URLs
// PURPOSE: Extracts PR index and repository path from PR HTML URLs without I/O

package iw.core.model

/** Pure utilities for parsing Forgejo pull request HTML URLs.
  *
  * Forgejo PR URLs follow the pattern: `https://host/owner/repo/pulls/N`
  */
object ForgejoUrl:

  /** Extract the numeric pull request index from a Forgejo PR HTML URL.
    *
    * Expects the URL to end with `/pulls/{index}`.
    *
    * @param prUrl
    *   Full PR HTML URL (e.g., "https://codeberg.org/owner/repo/pulls/42")
    * @return
    *   Right(index) on success, Left(error message) if not parseable
    */
  def extractPullRequestIndex(prUrl: String): Either[String, Int] =
    val pullsPattern = ".*/pulls/(\\d+)$".r
    prUrl match
      case pullsPattern(index) =>
        index.toIntOption.toRight(
          s"Cannot parse PR index from URL: $prUrl"
        )
      case _ =>
        Left(s"Cannot extract PR index from URL: $prUrl (expected .../pulls/N)")

  /** Extract the repository (owner/repo) from a Forgejo PR HTML URL.
    *
    * Strips the scheme+host prefix and the trailing `/pulls/{index}` suffix to
    * leave the `owner/repo` path.
    *
    * @param prUrl
    *   Full PR HTML URL (e.g., "https://codeberg.org/owner/repo/pulls/42")
    * @return
    *   Right("owner/repo") on success, Left(error message) if not parseable
    */
  def extractRepositoryFromPrUrl(prUrl: String): Either[String, String] =
    val pattern = "https?://[^/]+/(.+)/pulls/\\d+$".r
    prUrl match
      case pattern(repo) => Right(repo)
      case _             =>
        Left(
          s"Cannot extract repository from PR URL: $prUrl (expected scheme://host/owner/repo/pulls/N)"
        )
