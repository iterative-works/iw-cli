// PURPOSE: Unit tests for CreationErrorView component
// PURPOSE: Tests error state rendering with title, message, suggestion, and retry button

package iw.core.presentation.views

import munit.FunSuite
import iw.core.domain.UserFriendlyError

class CreationErrorViewTest extends FunSuite:

  val errorWithRetry = UserFriendlyError(
    title = "Git Operation Failed",
    message = "Failed to create git worktree",
    suggestion = None,
    canRetry = true,
    issueId = Some("IW-79")
  )

  val errorWithSuggestion = UserFriendlyError(
    title = "Directory Already Exists",
    message = "Directory 'iw-cli-IW-79' already exists on disk.",
    suggestion = Some("Remove the directory or register the existing worktree"),
    canRetry = false
  )

  val errorNoRetryNoSuggestion = UserFriendlyError(
    title = "Issue Not Found",
    message = "Could not find issue 'INVALID-999'",
    suggestion = None,
    canRetry = false
  )

  test("render includes error title"):
    val html = CreationErrorView.render(errorWithRetry).render

    assert(html.contains("Git Operation Failed"), "Should show error title")

  test("render includes error message"):
    val html = CreationErrorView.render(errorWithRetry).render

    assert(html.contains("Failed to create git worktree"), "Should show error message")

  test("render shows suggestion when present"):
    val html = CreationErrorView.render(errorWithSuggestion).render

    assert(html.contains("Remove the directory or register"), "Should show suggestion")

  test("render does not show suggestion section when not present"):
    val html = CreationErrorView.render(errorWithRetry).render

    // Should not have a suggestion section/container when suggestion is None
    // The exact check depends on implementation, but we verify the suggestion text is not there
    assert(!html.contains("Remove the directory"), "Should not show suggestion text from other errors")

  test("render includes retry button when canRetry is true"):
    val html = CreationErrorView.render(errorWithRetry).render

    assert(html.contains("Retry") || html.contains("Try Again"), "Should have retry button")

  test("retry button has hx-post to /api/worktrees/create"):
    val html = CreationErrorView.render(errorWithRetry).render

    assert(html.contains("hx-post"), "Should have hx-post attribute")
    assert(html.contains("/api/worktrees/create"), "Should post to worktrees/create endpoint")

  test("retry button targets modal body"):
    val html = CreationErrorView.render(errorWithRetry).render

    assert(html.contains("hx-target"), "Should have hx-target attribute")
    assert(html.contains("#modal-body-content") || html.contains("modal"), "Should target modal")

  test("render does not include retry button when canRetry is false"):
    val html = CreationErrorView.render(errorWithSuggestion).render

    // Should not have retry button when canRetry is false
    // We can check that there's no "Retry" text or that retry-specific attributes are missing
    val hasRetryButton = html.contains("Retry") || html.contains("Try Again")
    assert(!hasRetryButton, "Should not have retry button when canRetry is false")

  test("render includes dismiss button"):
    val html = CreationErrorView.render(errorWithRetry).render

    assert(html.contains("Dismiss") || html.contains("Close"), "Should have dismiss button")

  test("dismiss button closes modal"):
    val html = CreationErrorView.render(errorWithRetry).render

    // Check for HTMX attributes to dismiss modal via /api/modal/close endpoint
    assert(html.contains("hx-get") && html.contains("/api/modal/close"), "Should use hx-get to close modal")

  test("render has CSS class for error state"):
    val html = CreationErrorView.render(errorWithRetry).render

    assert(html.contains("creation-error") || html.contains("error"), "Should have error CSS class")

  test("render has error icon"):
    val html = CreationErrorView.render(errorWithRetry).render

    // Check for error indicator (X, exclamation, or similar)
    assert(html.contains("error-icon") || html.contains("✗") || html.contains("&#x2717;") ||
      html.contains("!") || html.contains("&#x26A0;"),
      "Should have error icon")

  test("render returns valid HTML fragment"):
    val frag = CreationErrorView.render(errorWithRetry)

    val html = frag.render
    assert(html.nonEmpty, "Should produce non-empty HTML")

  test("render with all error properties"):
    val html = CreationErrorView.render(errorWithSuggestion).render

    assert(html.contains("Directory Already Exists"), "Should have title")
    assert(html.contains("iw-cli-IW-79"), "Should have message")
    assert(html.contains("Remove the directory"), "Should have suggestion")
    assert(html.contains("Dismiss") || html.contains("Close"), "Should have dismiss button")

  test("retry button includes issueId in hx-vals when provided"):
    val html = CreationErrorView.render(errorWithRetry).render

    assert(html.contains("hx-vals"), "Should have hx-vals attribute for retry data")
    assert(html.contains("issueId"), "hx-vals should include issueId")
    assert(html.contains("IW-79"), "hx-vals should include the actual issue ID value")

  test("retry button without issueId does not have hx-vals"):
    val errorNoIssueId = UserFriendlyError(
      title = "Git Error",
      message = "Failed",
      suggestion = None,
      canRetry = true,
      issueId = None
    )
    val html = CreationErrorView.render(errorNoIssueId).render

    assert(html.contains("Retry") || html.contains("Try Again"), "Should have retry button")
    assert(!html.contains("hx-vals"), "Should not have hx-vals when issueId is None")

  test("retry button has hx-target-4xx and hx-target-5xx for error response handling"):
    val html = CreationErrorView.render(errorWithRetry).render

    assert(html.contains("hx-target-4xx"), "Should have hx-target-4xx attribute")
    assert(html.contains("hx-target-5xx"), "Should have hx-target-5xx attribute")
