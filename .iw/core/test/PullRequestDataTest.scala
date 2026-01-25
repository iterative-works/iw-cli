// PURPOSE: Unit tests for PullRequestData domain model
// PURPOSE: Tests PR state badge generation for open, merged, and closed states

package iw.core.domain

import munit.FunSuite
import iw.core.model.PullRequestData

class PullRequestDataTest extends FunSuite:

  test("stateBadgeClass returns 'pr-open' for Open state"):
    val pr = PullRequestData("https://github.com/org/repo/pull/42", PRState.Open, 42, "Add feature")
    assertEquals(pr.stateBadgeClass, "pr-open")

  test("stateBadgeClass returns 'pr-merged' for Merged state"):
    val pr = PullRequestData("https://github.com/org/repo/pull/42", PRState.Merged, 42, "Add feature")
    assertEquals(pr.stateBadgeClass, "pr-merged")

  test("stateBadgeClass returns 'pr-closed' for Closed state"):
    val pr = PullRequestData("https://github.com/org/repo/pull/42", PRState.Closed, 42, "Add feature")
    assertEquals(pr.stateBadgeClass, "pr-closed")

  test("stateBadgeText returns 'Open' for Open state"):
    val pr = PullRequestData("url", PRState.Open, 1, "Title")
    assertEquals(pr.stateBadgeText, "Open")

  test("stateBadgeText returns 'Merged' for Merged state"):
    val pr = PullRequestData("url", PRState.Merged, 1, "Title")
    assertEquals(pr.stateBadgeText, "Merged")

  test("stateBadgeText returns 'Closed' for Closed state"):
    val pr = PullRequestData("url", PRState.Closed, 1, "Title")
    assertEquals(pr.stateBadgeText, "Closed")

  test("PullRequestData stores all fields correctly"):
    val pr = PullRequestData(
      url = "https://github.com/org/repo/pull/123",
      state = PRState.Open,
      number = 123,
      title = "Implement Phase 6"
    )

    assertEquals(pr.url, "https://github.com/org/repo/pull/123")
    assertEquals(pr.state, PRState.Open)
    assertEquals(pr.number, 123)
    assertEquals(pr.title, "Implement Phase 6")

  test("PullRequestData with GitLab URL"):
    val pr = PullRequestData(
      url = "https://gitlab.com/org/repo/-/merge_requests/42",
      state = PRState.Merged,
      number = 42,
      title = "Fix bug"
    )

    assertEquals(pr.url, "https://gitlab.com/org/repo/-/merge_requests/42")
    assertEquals(pr.stateBadgeClass, "pr-merged")
