// PURPOSE: Unit tests for IssueSearchResult domain model
// PURPOSE: Tests value object creation and field validation

package iw.core.domain

import munit.FunSuite
import iw.core.dashboard.IssueSearchResult

class IssueSearchResultTest extends FunSuite:

  test("create IssueSearchResult with all fields"):
    val result = IssueSearchResult(
      id = "IW-79",
      title = "Add modal for worktree creation",
      status = "In Progress",
      url = "https://linear.app/team/issue/IW-79"
    )

    assertEquals(result.id, "IW-79")
    assertEquals(result.title, "Add modal for worktree creation")
    assertEquals(result.status, "In Progress")
    assertEquals(result.url, "https://linear.app/team/issue/IW-79")

  test("create IssueSearchResult with empty title"):
    val result = IssueSearchResult(
      id = "TEST-1",
      title = "",
      status = "Todo",
      url = "https://example.com/TEST-1"
    )

    assertEquals(result.title, "")

  test("create IssueSearchResult with different tracker URLs"):
    val linearResult = IssueSearchResult(
      id = "IWLE-100",
      title = "Linear issue",
      status = "Done",
      url = "https://linear.app/iterative-works/issue/IWLE-100"
    )

    val githubResult = IssueSearchResult(
      id = "IW-79",
      title = "GitHub issue",
      status = "Open",
      url = "https://github.com/iterative-works/iw-cli/issues/79"
    )

    val youtrackResult = IssueSearchResult(
      id = "PROJ-123",
      title = "YouTrack issue",
      status = "In Progress",
      url = "https://youtrack.example.com/issue/PROJ-123"
    )

    assertEquals(linearResult.url, "https://linear.app/iterative-works/issue/IWLE-100")
    assertEquals(githubResult.url, "https://github.com/iterative-works/iw-cli/issues/79")
    assertEquals(youtrackResult.url, "https://youtrack.example.com/issue/PROJ-123")

  test("IssueSearchResult is a case class with equality"):
    val result1 = IssueSearchResult("TEST-1", "Title", "Status", "url")
    val result2 = IssueSearchResult("TEST-1", "Title", "Status", "url")
    val result3 = IssueSearchResult("TEST-2", "Title", "Status", "url")

    assertEquals(result1, result2)
    assert(result1 != result3, "Different IDs should not be equal")
