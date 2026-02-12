// PURPOSE: Unit tests for CreationSuccessView component
// PURPOSE: Tests success state rendering with tmux command and copy button

package iw.core.presentation.views

import munit.FunSuite
import iw.core.dashboard.domain.WorktreeCreationResult
import iw.core.model.Check
import iw.core.dashboard.presentation.views.CreationSuccessView

class CreationSuccessViewTest extends FunSuite:

  val testResult = WorktreeCreationResult(
    issueId = "IW-79",
    worktreePath = "/home/user/projects/iw-cli-IW-79",
    tmuxSessionName = "iw-cli-IW-79",
    tmuxAttachCommand = "tmux attach -t iw-cli-IW-79"
  )

  test("render includes success message"):
    val html = CreationSuccessView.render(testResult).render

    assert(html.contains("Worktree Created"), "Should show success message")

  test("render includes issue ID in message"):
    val html = CreationSuccessView.render(testResult).render

    assert(html.contains("IW-79"), "Should show issue ID")

  test("render includes tmux attach command"):
    val html = CreationSuccessView.render(testResult).render

    assert(html.contains("tmux attach -t iw-cli-IW-79"), "Should show attach command")

  test("render includes copy button"):
    val html = CreationSuccessView.render(testResult).render

    assert(html.contains("Copy"), "Should have copy button text")

  test("copy button has onclick handler with clipboard.writeText"):
    val html = CreationSuccessView.render(testResult).render

    assert(html.contains("navigator.clipboard.writeText"), "Should have clipboard API call")
    assert(html.contains("tmux attach -t iw-cli-IW-79"), "Should include command in clipboard call")

  test("render includes close button"):
    val html = CreationSuccessView.render(testResult).render

    assert(html.contains("Close"), "Should have close button")

  test("close button has HTMX attributes to dismiss modal"):
    val html = CreationSuccessView.render(testResult).render

    // Check for HTMX attributes to dismiss modal via /api/modal/close endpoint
    assert(html.contains("hx-get") && html.contains("/api/modal/close"), "Should use hx-get to close modal")

  test("render includes worktree path"):
    val html = CreationSuccessView.render(testResult).render

    assert(html.contains("/home/user/projects/iw-cli-IW-79"), "Should show worktree path")

  test("render has success icon"):
    val html = CreationSuccessView.render(testResult).render

    // Check for checkmark or success indicator
    assert(html.contains("success") || html.contains("✓") || html.contains("&#x2714;"),
      "Should have success icon")

  test("render has CSS class for success state"):
    val html = CreationSuccessView.render(testResult).render

    assert(html.contains("creation-success"), "Should have creation-success class")

  test("tmux command is in code block"):
    val html = CreationSuccessView.render(testResult).render

    assert(html.contains("<code>"), "Should have code element")
    assert(html.contains("tmux attach"), "Code should contain command")

  test("render returns valid HTML fragment"):
    val frag = CreationSuccessView.render(testResult)

    val html = frag.render
    assert(html.nonEmpty, "Should produce non-empty HTML")
