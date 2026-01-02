// PURPOSE: Unit tests for SearchResultsView component
// PURPOSE: Tests search results rendering including empty state and result items

package iw.core.presentation.views

import munit.FunSuite
import iw.core.domain.IssueSearchResult

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
