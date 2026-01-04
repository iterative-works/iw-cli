// PURPOSE: Presentation layer for rendering worktree creation error state
// PURPOSE: Generates HTML for error messages with retry and dismiss actions

package iw.core.presentation.views

import scalatags.Text.all.*
import iw.core.domain.UserFriendlyError

object CreationErrorView:
  /** Render error state after worktree creation failure.
    *
    * Shows:
    * - Error icon
    * - Error title
    * - Error message
    * - Suggestion (if available)
    * - Retry button (if canRetry, includes issueId for server)
    * - Dismiss button
    *
    * @param error User-friendly error with title, message, suggestion, retry flag, and issueId
    * @return HTML fragment for error state
    */
  def render(error: UserFriendlyError): Frag =
    div(
      cls := "creation-error",
      renderErrorIcon(),
      h3(error.title),
      p(cls := "error-message", error.message),
      error.suggestion.map(renderSuggestion),
      renderButtons(error.canRetry, error.issueId)
    )

  /** Render error icon (X mark).
    *
    * @return HTML fragment for error icon
    */
  private def renderErrorIcon(): Frag =
    div(
      cls := "error-icon",
      raw("&#x2717;") // Unicode X mark
    )

  /** Render suggestion text if present.
    *
    * @param suggestion Suggestion text
    * @return HTML fragment for suggestion
    */
  private def renderSuggestion(suggestion: String): Frag =
    p(
      cls := "error-suggestion",
      strong("Suggestion: "),
      suggestion
    )

  /** Render action buttons (retry if applicable, and dismiss).
    *
    * @param canRetry Whether retry button should be shown
    * @param issueId Optional issue ID for retry request
    * @return HTML fragment for buttons
    */
  private def renderButtons(canRetry: Boolean, issueId: Option[String]): Frag =
    div(
      cls := "error-actions",
      if canRetry then renderRetryButton(issueId) else frag(),
      renderDismissButton()
    )

  /** Render retry button that re-posts the creation request.
    *
    * The retry button includes the issue ID in hx-vals so the server
    * can process the retry request correctly.
    *
    * @param issueId Optional issue ID to include in retry request
    * @return HTML fragment for retry button
    */
  private def renderRetryButton(issueId: Option[String]): Frag =
    val baseAttrs = Seq(
      cls := "btn-primary retry-btn",
      attr("hx-post") := "/api/worktrees/create",
      attr("hx-target") := "#modal-body-content",
      attr("hx-swap") := "innerHTML",
      attr("hx-indicator") := "#creation-spinner"
    )

    // Include issueId in request if available (properly escaped)
    val valsAttr = issueId.map { id =>
      val escapedId = id.replace("\"", "\\\"")
      attr("hx-vals") := s"""{"issueId": "$escapedId"}"""
    }

    button(
      baseAttrs ++ valsAttr.toSeq,
      "Retry"
    )

  /** Render dismiss button that closes the modal.
    *
    * @return HTML fragment for dismiss button
    */
  private def renderDismissButton(): Frag =
    button(
      cls := "btn-secondary close-modal-btn",
      attr("hx-on:click") := "document.getElementById('modal-container').innerHTML = ''",
      "Dismiss"
    )
