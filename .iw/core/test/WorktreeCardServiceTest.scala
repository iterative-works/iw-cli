// PURPOSE: Unit tests for WorktreeCardService per-card rendering
// PURPOSE: Validates single card refresh with throttling and error handling

package iw.tests

import iw.core.dashboard.{WorktreeCardService, RefreshThrottle}
import iw.core.dashboard.CardRenderResult
import iw.core.model.{WorktreeRegistration, IssueData, CachedIssue}
import iw.core.model.{Issue, IssueId}
import java.time.Instant
import java.time.temporal.ChronoUnit

class WorktreeCardServiceTest extends munit.FunSuite:
  test("renderCard returns HTML fragment with card content") {
    val now = Instant.now()
    val issueId = "IW-1"
    val worktree = WorktreeRegistration(
      issueId = issueId,
      path = "/tmp/worktree",
      trackerType = "Linear",
      team = "IW",
      registeredAt = now,
      lastSeenAt = now
    )
    val issueData = IssueData(
      id = issueId,
      title = "Test Issue",
      status = "In Progress",
      assignee = Some("Test User"),
      description = None,
      url = "https://example.com/issue/IW-1",
      fetchedAt = now
    )
    val cache = Map(issueId -> CachedIssue(issueData))
    val throttle = RefreshThrottle()

    val result = WorktreeCardService.renderCard(
      issueId,
      Map(issueId -> worktree),
      cache,
      Map.empty,
      Map.empty,
      Map.empty,
      throttle,
      now,
      "test-server",
      (id: String) => Right(Issue(id, "Test Issue From API", "Open", None, None)),
      (id: String, tracker: String, config: Option[String]) => s"https://example.com/issue/$id"
    )

    assert(result.html.contains("Test Issue From API"))
    assert(result.html.contains("worktree-card"))
  }

  test("renderCard returns empty string for unknown worktree") {
    val now = Instant.now()
    val throttle = RefreshThrottle()

    val result = WorktreeCardService.renderCard(
      "UNKNOWN-1",
      Map.empty,
      Map.empty,
      Map.empty,
      Map.empty,
      Map.empty,
      throttle,
      now,
      "test-server",
      (id: String) => Left("Not found"),
      (id: String, tracker: String, config: Option[String]) => s"https://example.com/issue/$id"
    )

    assertEquals(result.html, "")
  }

  test("renderCard includes HTMX attributes for polling") {
    val now = Instant.now()
    val issueId = "IW-1"
    val worktree = WorktreeRegistration(
      issueId = issueId,
      path = "/tmp/worktree",
      trackerType = "Linear",
      team = "IW",
      registeredAt = now,
      lastSeenAt = now
    )
    val cache = Map.empty[String, CachedIssue] // Empty cache forces fetch
    val throttle = RefreshThrottle()

    val result = WorktreeCardService.renderCard(
      issueId,
      Map(issueId -> worktree),
      cache,
      Map.empty,
      Map.empty,
      Map.empty,
      throttle,
      now,
      "test-server",
      (id: String) => Right(Issue(id, "Test Issue", "Open", None, None)),
      (id: String, tracker: String, config: Option[String]) => s"https://example.com/issue/$id"
    )

    assert(result.html.contains("hx-get"))
    assert(result.html.contains(s"/worktrees/$issueId/card"))
    assert(result.html.contains("hx-trigger"))
    assert(result.html.contains("hx-swap"))
    assert(result.html.contains("outerHTML"))
  }

  test("renderCard includes unique ID for HTMX targeting") {
    val now = Instant.now()
    val issueId = "IW-1"
    val worktree = WorktreeRegistration(
      issueId = issueId,
      path = "/tmp/worktree",
      trackerType = "Linear",
      team = "IW",
      registeredAt = now,
      lastSeenAt = now
    )
    val cache = Map.empty[String, CachedIssue] // Empty cache forces fetch
    val throttle = RefreshThrottle()

    val result = WorktreeCardService.renderCard(
      issueId,
      Map(issueId -> worktree),
      cache,
      Map.empty,
      Map.empty,
      Map.empty,
      throttle,
      now,
      "test-server",
      (id: String) => Right(Issue(id, "Test Issue", "Open", None, None)),
      (id: String, tracker: String, config: Option[String]) => s"https://example.com/issue/$id"
    )

    assert(result.html.contains(s"id=\"card-$issueId\""))
  }
