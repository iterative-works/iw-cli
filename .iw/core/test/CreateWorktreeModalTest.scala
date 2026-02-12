// PURPOSE: Unit tests for CreateWorktreeModal view component
// PURPOSE: Tests modal HTML structure, HTMX attributes, and search input rendering

package iw.core.presentation.views

import munit.FunSuite
import iw.core.model.Check
import iw.core.dashboard.presentation.views.CreateWorktreeModal

class CreateWorktreeModalTest extends FunSuite:

  test("render creates modal with correct structure"):
    val html = CreateWorktreeModal.render().render

    assert(html.contains("id=\"create-worktree-modal\""), "Modal should have correct ID")
    assert(html.contains("class=\"modal\""), "Modal should have modal class")
    assert(html.contains("class=\"modal-backdrop\""), "Modal should have backdrop")
    assert(html.contains("class=\"modal-content\""), "Modal should have content div")

  test("render includes modal header with title"):
    val html = CreateWorktreeModal.render().render

    assert(html.contains("class=\"modal-header\""), "Should have modal header")
    assert(html.contains("Create Worktree"), "Should have title text")

  test("render includes close button with HTMX attributes"):
    val html = CreateWorktreeModal.render().render

    assert(html.contains("class=\"modal-close\""), "Should have close button")
    assert(html.contains("×"), "Should have × symbol")
    // Close button uses HTMX to call /api/modal/close endpoint
    assert(html.contains("hx-get") && html.contains("/api/modal/close"), "Close button should use hx-get to close modal")

  test("render includes search input with correct attributes"):
    val html = CreateWorktreeModal.render().render

    assert(html.contains("id=\"issue-search-input\""), "Should have search input ID")
    assert(html.contains("type=\"text\""), "Should be text input")
    assert(html.contains("placeholder=\"Search by issue ID"), "Should have placeholder")
    assert(html.contains("name=\"q\""), "Should have query parameter name")

  test("search input includes HTMX debounce attributes"):
    val html = CreateWorktreeModal.render().render

    assert(html.contains("hx-get=\"/api/issues/search\""), "Should target search endpoint")
    assert(html.contains("hx-trigger=\"keyup changed delay:300ms\""), "Should have debounce")
    assert(html.contains("hx-target=\"#search-results\""), "Should target results div")

  test("render includes search results container"):
    val html = CreateWorktreeModal.render().render

    assert(html.contains("id=\"search-results\""), "Should have search results div")
    assert(html.contains("class=\"search-results\""), "Should have search results class")

  test("render includes modal body"):
    val html = CreateWorktreeModal.render().render

    assert(html.contains("class=\"modal-body\""), "Should have modal body")

  test("render returns valid HTML fragment"):
    val frag = CreateWorktreeModal.render()

    // Should not throw when rendered
    val html = frag.render
    assert(html.nonEmpty, "Should produce non-empty HTML")

  test("render includes loading indicator element"):
    val html = CreateWorktreeModal.render().render

    assert(html.contains("id=\"creation-spinner\""), "Should have creation spinner element")

  test("loading indicator has htmx-indicator class"):
    val html = CreateWorktreeModal.render().render

    assert(html.contains("htmx-indicator"), "Should have htmx-indicator class for auto show/hide")

  test("modal body has wrapper div for content swap"):
    val html = CreateWorktreeModal.render().render

    assert(html.contains("id=\"modal-body-content\""), "Should have modal-body-content div for HTMX target")

  test("search results container has hx-trigger load for auto-load"):
    val html = CreateWorktreeModal.render().render

    assert(html.contains("hx-trigger=\"load\""), "Should have load trigger to auto-load recent issues")

  test("search results container has hx-get pointing to recent endpoint"):
    val html = CreateWorktreeModal.render().render

    assert(html.contains("hx-get=\"/api/issues/recent\""), "Should target recent issues endpoint")

  test("search results container has hx-swap innerHTML"):
    val html = CreateWorktreeModal.render().render

    // Note: hx-swap="innerHTML" appears twice - on close button and search results
    // Check for search-results context by looking for both attributes together
    assert(html.contains("id=\"search-results\"") && html.contains("hx-swap=\"innerHTML\""),
      "Search results should use innerHTML swap for content replacement")

  test("search results container includes project parameter when projectPath provided"):
    val html = CreateWorktreeModal.render(Some("/path/to/project")).render

    assert(html.contains("hx-get=\"/api/issues/recent?project="), "Should include project parameter in URL")
    assert(html.contains("%2Fpath%2Fto%2Fproject"), "Should have URL-encoded project path")
