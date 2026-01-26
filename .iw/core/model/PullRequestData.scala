// PURPOSE: Domain model for pull request information
// PURPOSE: Represents PR state and provides badge styling for display

package iw.core.model

/** Pull request state enumeration.
  * Maps to GitHub (OPEN, MERGED, CLOSED) and GitLab (opened, merged, closed) states.
  */
enum PRState:
  case Open, Merged, Closed

/** Pull request information from GitHub or GitLab.
  *
  * @param url PR URL (e.g., "https://github.com/org/repo/pull/42")
  * @param state PR state (Open, Merged, or Closed)
  * @param number PR number (e.g., 42)
  * @param title PR title (e.g., "Add feature X")
  */
case class PullRequestData(
  url: String,
  state: PRState,
  number: Int,
  title: String
):
  /** CSS class for PR state badge.
    * Returns "pr-open" for Open, "pr-merged" for Merged, "pr-closed" for Closed.
    */
  def stateBadgeClass: String = state match
    case PRState.Open => "pr-open"
    case PRState.Merged => "pr-merged"
    case PRState.Closed => "pr-closed"

  /** Badge text for PR state display.
    * Returns "Open", "Merged", or "Closed".
    */
  def stateBadgeText: String = state match
    case PRState.Open => "Open"
    case PRState.Merged => "Merged"
    case PRState.Closed => "Closed"
