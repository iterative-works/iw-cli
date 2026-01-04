// PURPOSE: Domain model for issue search results
// PURPOSE: Represents a single search result with issue ID, title, status, and URL

package iw.core.domain

/** Issue search result with essential fields for display.
  *
  * @param id Issue identifier (e.g., "IW-79", "IWLE-100", "PROJ-123")
  * @param title Issue title
  * @param status Issue status (e.g., "In Progress", "Done", "Open")
  * @param url Direct link to issue in tracker
  * @param hasWorktree Whether this issue already has a registered worktree
  */
case class IssueSearchResult(
  id: String,
  title: String,
  status: String,
  url: String,
  hasWorktree: Boolean = false
)
