// PURPOSE: User-facing error representation with actionable suggestions
// PURPOSE: Maps technical errors to friendly messages with retry capability

package iw.core.domain

/** User-friendly error for display in the UI.
  *
  * @param title Short error title for display
  * @param message Detailed error message
  * @param suggestion Optional suggestion for how to resolve the error
  * @param canRetry Whether the operation can be retried
  * @param issueId Optional issue ID for retry functionality
  */
case class UserFriendlyError(
  title: String,
  message: String,
  suggestion: Option[String],
  canRetry: Boolean,
  issueId: Option[String] = None
)
