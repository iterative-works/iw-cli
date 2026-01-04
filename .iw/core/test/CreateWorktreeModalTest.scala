// PURPOSE: Unit tests for CreateWorktreeModal view component
// PURPOSE: Tests modal HTML structure, HTMX attributes, and search input rendering

package iw.core.presentation.views

import munit.FunSuite

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
    // Close button should clear the modal
    assert(html.contains("hx-swap=\"innerHTML\""), "Close button should use innerHTML swap")

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
