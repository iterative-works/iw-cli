// PURPOSE: Unit tests for SearchResultsView component
// PURPOSE: Tests search results rendering including empty state and result items

package iw.core.presentation.views

import munit.FunSuite
import iw.core.dashboard.IssueSearchResult
import iw.core.model.Issue
import iw.core.model.Check
import iw.core.dashboard.presentation.views.SearchResultsView

class SearchResultsViewTest extends FunSuite:

  test("render with empty results shows no issues found message"):
    val html = SearchResultsView.render(List.empty).render

    assert(html.contains("No issues found"), "Should show empty state message")

  test("render with single result shows result item"):
    val result = IssueSearchResult(
      id = "IW-79",
      title = "Add modal for worktree creation",
      status = "In Progress",
      url = "https://linear.app/team/issue/IW-79"
    )

    val html = SearchResultsView.render(List(result)).render

    assert(html.contains("IW-79"), "Should show issue ID")
    assert(html.contains("Add modal for worktree creation"), "Should show title")
    assert(html.contains("In Progress"), "Should show status")

  test("render with multiple results shows all items"):
    val results = List(
      IssueSearchResult("IW-79", "First Issue", "In Progress", "url1"),
      IssueSearchResult("IW-80", "Second Issue", "Done", "url2"),
      IssueSearchResult("IW-81", "Third Issue", "To Do", "url3")
    )

    val html = SearchResultsView.render(results).render

    assert(html.contains("IW-79"), "Should show first issue")
    assert(html.contains("IW-80"), "Should show second issue")
    assert(html.contains("IW-81"), "Should show third issue")
    assert(html.contains("First Issue"), "Should show first title")
    assert(html.contains("Second Issue"), "Should show second title")
    assert(html.contains("Third Issue"), "Should show third title")

  test("result item includes issue ID with correct CSS class"):
    val result = IssueSearchResult("TEST-1", "Title", "Status", "url")

    val html = SearchResultsView.render(List(result)).render

    assert(html.contains("class=\"search-result-id\""), "Should have ID class")
    assert(html.contains("TEST-1"), "Should show ID")

  test("result item includes title with correct CSS class"):
    val result = IssueSearchResult("TEST-1", "Test Title", "Status", "url")

    val html = SearchResultsView.render(List(result)).render

    assert(html.contains("class=\"search-result-title\""), "Should have title class")
    assert(html.contains("Test Title"), "Should show title")

  test("result item includes status with correct CSS class"):
    val result = IssueSearchResult("TEST-1", "Title", "In Progress", "url")

    val html = SearchResultsView.render(List(result)).render

    assert(html.contains("class=\"search-result-status\""), "Should have status class")
    assert(html.contains("In Progress"), "Should show status")

  test("result item is wrapped in clickable div"):
    val result = IssueSearchResult("TEST-1", "Title", "Status", "url")

    val html = SearchResultsView.render(List(result)).render

    assert(html.contains("class=\"search-result-item\""), "Should have result item class")

  test("render limits results to maximum 10 items"):
    // Create 15 results
    val results = (1 to 15).map { i =>
      IssueSearchResult(s"TEST-$i", s"Title $i", "Status", "url")
    }.toList

    val html = SearchResultsView.render(results).render

    // Count occurrences of result-item divs
    val itemCount = "search-result-item".r.findAllIn(html).length
    assertEquals(itemCount, 10, "Should limit to 10 results")

    // Check that TEST-10 is included but TEST-11 is not
    assert(html.contains("TEST-10"), "Should include 10th item")
    assert(!html.contains("TEST-11"), "Should not include 11th item")

  test("empty state has appropriate styling class"):
    val html = SearchResultsView.render(List.empty).render

    assert(html.contains("class=\"search-empty-state\""), "Should have empty state class")

  test("render returns valid HTML fragment"):
    val result = IssueSearchResult("TEST-1", "Title", "Status", "url")
    val frag = SearchResultsView.render(List(result))

    // Should not throw when rendered
    val html = frag.render
    assert(html.nonEmpty, "Should produce non-empty HTML")

  test("result item has hx-post attribute for worktree creation"):
    val result = IssueSearchResult("TEST-1", "Title", "Status", "url")

    val html = SearchResultsView.render(List(result)).render

    assert(html.contains("hx-post"), "Should have hx-post attribute")
    assert(html.contains("/api/worktrees/create"), "Should POST to creation endpoint")

  test("result item has hx-vals with issue ID"):
    val result = IssueSearchResult("IW-79", "Title", "Status", "url")

    val html = SearchResultsView.render(List(result)).render

    assert(html.contains("hx-vals"), "Should have hx-vals attribute")
    assert(html.contains("issueId"), "Should include issueId in vals")
    assert(html.contains("IW-79"), "Should include actual issue ID")

  test("result item has hx-target for modal body"):
    val result = IssueSearchResult("TEST-1", "Title", "Status", "url")

    val html = SearchResultsView.render(List(result)).render

    assert(html.contains("hx-target"), "Should have hx-target attribute")
    assert(html.contains("#modal-body-content"), "Should target modal body")

  test("result item has hx-swap set to innerHTML"):
    val result = IssueSearchResult("TEST-1", "Title", "Status", "url")

    val html = SearchResultsView.render(List(result)).render

    assert(html.contains("hx-swap"), "Should have hx-swap attribute")
    assert(html.contains("innerHTML"), "Should use innerHTML swap strategy")

  test("result item has hx-indicator for loading spinner"):
    val result = IssueSearchResult("TEST-1", "Title", "Status", "url")

    val html = SearchResultsView.render(List(result)).render

    assert(html.contains("hx-indicator"), "Should have hx-indicator attribute")
    assert(html.contains("#creation-spinner"), "Should reference creation spinner")

  // Group F: Badge tests for issues with existing worktrees

  test("render with hasWorktree flag shows Already has worktree badge"):
    val result = IssueSearchResult("IW-79", "Title", "In Progress", "url", hasWorktree = true)

    val html = SearchResultsView.render(List(result)).render

    assert(html.contains("Already has worktree"), "Should show worktree badge")
    assert(html.contains("worktree-badge"), "Should have badge class")

  test("render without hasWorktree flag shows no badge"):
    val result = IssueSearchResult("IW-79", "Title", "In Progress", "url", hasWorktree = false)

    val html = SearchResultsView.render(List(result)).render

    assert(!html.contains("Already has worktree"), "Should not show worktree badge")
    assert(!html.contains("worktree-badge"), "Should not have badge class")

  test("result with existing worktree does not have hx-post attribute"):
    val result = IssueSearchResult("IW-79", "Title", "In Progress", "url", hasWorktree = true)

    val html = SearchResultsView.render(List(result)).render

    // The item should still be rendered but without create action
    assert(html.contains("IW-79"), "Should show issue ID")
    // Should not have creation POST endpoint
    val itemPattern = "IW-79.*?hx-post.*?/api/worktrees/create".r
    assert(itemPattern.findFirstIn(html).isEmpty, "Should not have create action for existing worktree")

  // Group F: UI state management tests

  test("results container has hx-on::before-request to disable UI"):
    val result = IssueSearchResult("IW-79", "Title", "Status", "url")

    val html = SearchResultsView.render(List(result)).render

    assert(html.contains("hx-on::before-request"), "Should have before-request handler")
    assert(html.contains("classList.add('disabled')"), "Should add disabled class on request start")

  test("results container has hx-on::after-request to re-enable UI"):
    val result = IssueSearchResult("IW-79", "Title", "Status", "url")

    val html = SearchResultsView.render(List(result)).render

    assert(html.contains("hx-on::after-request"), "Should have after-request handler")
    assert(html.contains("classList.remove('disabled')"), "Should remove disabled class on request end")

  test("result item has hx-target-4xx for error response handling"):
    val result = IssueSearchResult("TEST-1", "Title", "Status", "url")

    val html = SearchResultsView.render(List(result)).render

    assert(html.contains("hx-target-4xx"), "Should have hx-target-4xx attribute")
    assert(html.contains("hx-target-5xx"), "Should have hx-target-5xx attribute")
