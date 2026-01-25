// PURPOSE: Unit tests for WorktreeListView rendering
// PURPOSE: Verify HTML output for worktree cards including review artifacts section

package iw.core.test

import iw.core.model.{WorktreeRegistration, IssueData, WorkflowProgress, GitStatus, PullRequestData, ReviewState, ReviewArtifact}
import iw.core.dashboard.WorktreeListView
import java.time.Instant
import iw.core.dashboard.Check
import iw.core.model.Issue

class WorktreeListViewTest extends munit.FunSuite:

  val now = Instant.parse("2025-01-15T12:00:00Z")

  val sampleWorktree = WorktreeRegistration(
    issueId = "IWLE-123",
    path = "/path/to/worktree",
    trackerType = "Linear",
    team = "Engineering",
    registeredAt = Instant.parse("2025-01-10T10:00:00Z"),
    lastSeenAt = Instant.parse("2025-01-15T11:00:00Z")
  )

  val sampleIssueData = IssueData(
    id = "IWLE-123",
    title = "Test Issue",
    status = "In Progress",
    assignee = Some("Developer"),
    description = Some("Test issue description"),
    url = "https://linear.app/issue/IWLE-123",
    fetchedAt = Instant.parse("2025-01-15T11:30:00Z")
  )

  // Review Artifacts Section Tests

  test("WorktreeListView renders review section when reviewState provided with artifacts"):
    val reviewState = Some(Right(ReviewState(
      status = Some("awaiting_review"),
      phase = Some(1),
      message = Some("Ready for review"),
      artifacts = List(
        ReviewArtifact("Analysis", "project-management/issues/IWLE-123/analysis.md"),
        ReviewArtifact("Context", "project-management/issues/IWLE-123/phase-01-context.md")
      )
    )))

    val worktreesWithData = List(
      (sampleWorktree, Some((sampleIssueData, false, false)), None, None, None, reviewState)
    )

    val html = WorktreeListView.render(worktreesWithData, now, "localhost")
    val htmlStr = html.render

    assert(htmlStr.contains("Review Artifacts"), s"Should contain 'Review Artifacts' heading")
    assert(htmlStr.contains("review-artifacts"), s"Should contain 'review-artifacts' class")
    assert(htmlStr.contains("artifact-list"), s"Should contain 'artifact-list' class")
    assert(htmlStr.contains("Analysis"), s"Should contain artifact label 'Analysis'")
    assert(htmlStr.contains("Context"), s"Should contain artifact label 'Context'")

  test("WorktreeListView omits review section when reviewState is None"):
    val worktreesWithData = List(
      (sampleWorktree, Some((sampleIssueData, false, false)), None, None, None, None)
    )

    val html = WorktreeListView.render(worktreesWithData, now, "localhost")
    val htmlStr = html.render

    assert(!htmlStr.contains("Review Artifacts"), s"Should not contain 'Review Artifacts' when reviewState is None")
    assert(!htmlStr.contains("review-artifacts"), s"Should not contain 'review-artifacts' class")
    assert(!htmlStr.contains("artifact-list"), s"Should not contain 'artifact-list' class")

  test("WorktreeListView omits review section when artifacts list is empty"):
    val reviewState = Some(Right(ReviewState(
      status = Some("awaiting_review"),
      phase = Some(1),
      message = Some("Ready for review"),
      artifacts = List.empty  // Empty artifacts list
    )))

    val worktreesWithData = List(
      (sampleWorktree, Some((sampleIssueData, false, false)), None, None, None, reviewState)
    )

    val html = WorktreeListView.render(worktreesWithData, now, "localhost")
    val htmlStr = html.render

    assert(!htmlStr.contains("Review Artifacts"), s"Should not contain 'Review Artifacts' when artifacts list is empty")
    assert(!htmlStr.contains("review-artifacts"), s"Should not contain 'review-artifacts' class")

  test("WorktreeListView displays all artifact labels correctly"):
    val reviewState = Some(Right(ReviewState(
      status = None,
      phase = None,
      message = None,
      artifacts = List(
        ReviewArtifact("Analysis Doc", "path/to/analysis.md"),
        ReviewArtifact("Phase Context", "path/to/context.md"),
        ReviewArtifact("Review Packet", "path/to/review-packet.md")
      )
    )))

    val worktreesWithData = List(
      (sampleWorktree, Some((sampleIssueData, false, false)), None, None, None, reviewState)
    )

    val html = WorktreeListView.render(worktreesWithData, now, "localhost")
    val htmlStr = html.render

    assert(htmlStr.contains("Analysis Doc"), s"Should contain artifact label 'Analysis Doc'")
    assert(htmlStr.contains("Phase Context"), s"Should contain artifact label 'Phase Context'")
    assert(htmlStr.contains("Review Packet"), s"Should contain artifact label 'Review Packet'")
    // Verify artifacts are clickable links
    assert(htmlStr.contains("<a"), s"Artifacts should be links")
    assert(htmlStr.contains("/worktrees/"), s"Artifact links should point to worktree artifacts route")
    assert(htmlStr.contains("?path="), s"Artifact links should include path query parameter")

  test("WorktreeListView renders empty state when no worktrees"):
    val html = WorktreeListView.render(List.empty, now, "localhost")
    val htmlStr = html.render

    assert(htmlStr.contains("empty-state"), s"Should contain 'empty-state' class")
    assert(htmlStr.contains("No worktrees registered yet"), s"Should show empty state message")

  test("WorktreeListView renders multiple worktrees with different review states"):
    val wt1 = sampleWorktree.copy(issueId = "IWLE-1")
    val wt2 = sampleWorktree.copy(issueId = "IWLE-2")

    val reviewState1 = Some(Right(ReviewState(None, None, None, List(ReviewArtifact("Doc1", "doc1.md")))))
    val reviewState2 = None  // No review state

    val worktreesWithData = List(
      (wt1, Some((sampleIssueData, false, false)), None, None, None, reviewState1),
      (wt2, Some((sampleIssueData, false, false)), None, None, None, reviewState2)
    )

    val html = WorktreeListView.render(worktreesWithData, now, "localhost")
    val htmlStr = html.render

    // Should have one review section (for wt1) but not for wt2
    assert(htmlStr.contains("Doc1"), s"Should contain artifact for first worktree")
    // Count occurrences of "Review Artifacts" - should be exactly 1
    val reviewArtifactsCount = "Review Artifacts".r.findAllIn(htmlStr).length
    assertEquals(reviewArtifactsCount, 1, s"Should have exactly one 'Review Artifacts' section")

  // Helper Function Tests

  test("formatStatusLabel converts awaiting_review to Awaiting Review"):
    val result = WorktreeListView.formatStatusLabel("awaiting_review")
    assertEquals(result, "Awaiting Review")

  test("formatStatusLabel converts in_progress to In Progress"):
    val result = WorktreeListView.formatStatusLabel("in_progress")
    assertEquals(result, "In Progress")

  test("formatStatusLabel converts completed to Completed"):
    val result = WorktreeListView.formatStatusLabel("completed")
    assertEquals(result, "Completed")

  test("formatStatusLabel handles arbitrary strings"):
    val result = WorktreeListView.formatStatusLabel("custom_status_value")
    assertEquals(result, "Custom Status Value")

  test("statusBadgeClass maps awaiting_review to review-status-awaiting-review"):
    val result = WorktreeListView.statusBadgeClass("awaiting_review")
    assertEquals(result, "review-status-awaiting-review")

  test("statusBadgeClass maps in_progress to review-status-in-progress"):
    val result = WorktreeListView.statusBadgeClass("in_progress")
    assertEquals(result, "review-status-in-progress")

  test("statusBadgeClass maps completed to review-status-completed"):
    val result = WorktreeListView.statusBadgeClass("completed")
    assertEquals(result, "review-status-completed")

  test("statusBadgeClass maps unknown status to review-status-default"):
    val result = WorktreeListView.statusBadgeClass("unknown_status")
    assertEquals(result, "review-status-default")

  test("statusBadgeClass handles awaiting-review with hyphens"):
    val result = WorktreeListView.statusBadgeClass("awaiting-review")
    assertEquals(result, "review-status-awaiting-review")

  test("statusBadgeClass handles in-progress with hyphens"):
    val result = WorktreeListView.statusBadgeClass("in-progress")
    assertEquals(result, "review-status-in-progress")

  // Status Badge Rendering Tests

  test("renderWorktreeCard includes status badge when status is defined"):
    val reviewState = Some(Right(ReviewState(
      status = Some("awaiting_review"),
      phase = None,
      message = None,
      artifacts = List(ReviewArtifact("Analysis", "analysis.md"))
    )))

    val worktreesWithData = List(
      (sampleWorktree, Some((sampleIssueData, false, false)), None, None, None, reviewState)
    )

    val html = WorktreeListView.render(worktreesWithData, now, "localhost")
    val htmlStr = html.render

    assert(htmlStr.contains("review-status"), "Should contain review-status class")
    assert(htmlStr.contains("review-status-awaiting-review"), "Should contain specific status class")
    assert(htmlStr.contains("Awaiting Review"), "Should contain formatted status label")

  test("renderWorktreeCard includes status badge with correct class for in_progress"):
    val reviewState = Some(Right(ReviewState(
      status = Some("in_progress"),
      phase = None,
      message = None,
      artifacts = List(ReviewArtifact("Analysis", "analysis.md"))
    )))

    val worktreesWithData = List(
      (sampleWorktree, Some((sampleIssueData, false, false)), None, None, None, reviewState)
    )

    val html = WorktreeListView.render(worktreesWithData, now, "localhost")
    val htmlStr = html.render

    assert(htmlStr.contains("review-status-in-progress"), "Should contain in-progress status class")
    assert(htmlStr.contains("In Progress"), "Should contain In Progress label")

  test("renderWorktreeCard includes status badge with correct class for completed"):
    val reviewState = Some(Right(ReviewState(
      status = Some("completed"),
      phase = None,
      message = None,
      artifacts = List(ReviewArtifact("Analysis", "analysis.md"))
    )))

    val worktreesWithData = List(
      (sampleWorktree, Some((sampleIssueData, false, false)), None, None, None, reviewState)
    )

    val html = WorktreeListView.render(worktreesWithData, now, "localhost")
    val htmlStr = html.render

    assert(htmlStr.contains("review-status-completed"), "Should contain completed status class")
    assert(htmlStr.contains("Completed"), "Should contain Completed label")

  test("renderWorktreeCard omits status badge when status is None"):
    val reviewState = Some(Right(ReviewState(
      status = None,
      phase = None,
      message = None,
      artifacts = List(ReviewArtifact("Analysis", "analysis.md"))
    )))

    val worktreesWithData = List(
      (sampleWorktree, Some((sampleIssueData, false, false)), None, None, None, reviewState)
    )

    val html = WorktreeListView.render(worktreesWithData, now, "localhost")
    val htmlStr = html.render

    assert(!htmlStr.contains("review-status-awaiting-review"), "Should not contain status badge classes")
    assert(!htmlStr.contains("review-status-in-progress"), "Should not contain status badge classes")
    assert(!htmlStr.contains("review-status-completed"), "Should not contain status badge classes")

  // Phase Number Display Tests

  test("renderWorktreeCard includes phase number when phase is defined"):
    val reviewState = Some(Right(ReviewState(
      status = None,
      phase = Some(8),
      message = None,
      artifacts = List(ReviewArtifact("Analysis", "analysis.md"))
    )))

    val worktreesWithData = List(
      (sampleWorktree, Some((sampleIssueData, false, false)), None, None, None, reviewState)
    )

    val html = WorktreeListView.render(worktreesWithData, now, "localhost")
    val htmlStr = html.render

    assert(htmlStr.contains("review-phase"), "Should contain review-phase class")
    assert(htmlStr.contains("Phase 8"), "Should contain Phase 8 text")

  test("renderWorktreeCard displays phase 0 correctly"):
    val reviewState = Some(Right(ReviewState(
      status = None,
      phase = Some(0),
      message = None,
      artifacts = List(ReviewArtifact("Analysis", "analysis.md"))
    )))

    val worktreesWithData = List(
      (sampleWorktree, Some((sampleIssueData, false, false)), None, None, None, reviewState)
    )

    val html = WorktreeListView.render(worktreesWithData, now, "localhost")
    val htmlStr = html.render

    assert(htmlStr.contains("Phase 0"), "Should display Phase 0")

  test("renderWorktreeCard omits phase number when phase is None"):
    val reviewState = Some(Right(ReviewState(
      status = None,
      phase = None,
      message = None,
      artifacts = List(ReviewArtifact("Analysis", "analysis.md"))
    )))

    val worktreesWithData = List(
      (sampleWorktree, Some((sampleIssueData, false, false)), None, None, None, reviewState)
    )

    val html = WorktreeListView.render(worktreesWithData, now, "localhost")
    val htmlStr = html.render

    assert(!htmlStr.contains("review-phase"), "Should not contain review-phase class when phase is None")
    assert(!htmlStr.contains("Phase "), "Should not contain Phase text when phase is None")

  // Message Display Tests

  test("renderWorktreeCard includes message when message is defined"):
    val reviewState = Some(Right(ReviewState(
      status = None,
      phase = None,
      message = Some("Phase 8 complete - Ready for review"),
      artifacts = List(ReviewArtifact("Analysis", "analysis.md"))
    )))

    val worktreesWithData = List(
      (sampleWorktree, Some((sampleIssueData, false, false)), None, None, None, reviewState)
    )

    val html = WorktreeListView.render(worktreesWithData, now, "localhost")
    val htmlStr = html.render

    assert(htmlStr.contains("review-message"), "Should contain review-message class")
    assert(htmlStr.contains("Phase 8 complete - Ready for review"), "Should contain message text")

  test("renderWorktreeCard omits message when message is None"):
    val reviewState = Some(Right(ReviewState(
      status = None,
      phase = None,
      message = None,
      artifacts = List(ReviewArtifact("Analysis", "analysis.md"))
    )))

    val worktreesWithData = List(
      (sampleWorktree, Some((sampleIssueData, false, false)), None, None, None, reviewState)
    )

    val html = WorktreeListView.render(worktreesWithData, now, "localhost")
    val htmlStr = html.render

    assert(!htmlStr.contains("review-message"), "Should not contain review-message class when message is None")

  // Combined Rendering Tests

  test("renderWorktreeCard displays status, phase, and message together"):
    val reviewState = Some(Right(ReviewState(
      status = Some("awaiting_review"),
      phase = Some(8),
      message = Some("Ready for review"),
      artifacts = List(ReviewArtifact("Analysis", "analysis.md"))
    )))

    val worktreesWithData = List(
      (sampleWorktree, Some((sampleIssueData, false, false)), None, None, None, reviewState)
    )

    val html = WorktreeListView.render(worktreesWithData, now, "localhost")
    val htmlStr = html.render

    assert(htmlStr.contains("review-status-awaiting-review"), "Should contain status badge")
    assert(htmlStr.contains("Phase 8"), "Should contain phase number")
    assert(htmlStr.contains("Ready for review"), "Should contain message")

  test("renderWorktreeCard handles missing status, phase, and message gracefully"):
    val reviewState = Some(Right(ReviewState(
      status = None,
      phase = None,
      message = None,
      artifacts = List(ReviewArtifact("Analysis", "analysis.md"))
    )))

    val worktreesWithData = List(
      (sampleWorktree, Some((sampleIssueData, false, false)), None, None, None, reviewState)
    )

    val html = WorktreeListView.render(worktreesWithData, now, "localhost")
    val htmlStr = html.render

    // Should still render artifacts section
    assert(htmlStr.contains("Review Artifacts"), "Should contain Review Artifacts heading")
    assert(htmlStr.contains("Analysis"), "Should contain artifact")

    // Should NOT render status/phase/message elements
    assert(!htmlStr.contains("review-status-"), "Should not render status badge")
    assert(!htmlStr.contains("review-phase"), "Should not render phase")
    assert(!htmlStr.contains("review-message"), "Should not render message")

  test("renderWorktreeCard displays partial fields correctly (only status)"):
    val reviewState = Some(Right(ReviewState(
      status = Some("in_progress"),
      phase = None,
      message = None,
      artifacts = List(ReviewArtifact("Analysis", "analysis.md"))
    )))

    val worktreesWithData = List(
      (sampleWorktree, Some((sampleIssueData, false, false)), None, None, None, reviewState)
    )

    val html = WorktreeListView.render(worktreesWithData, now, "localhost")
    val htmlStr = html.render

    assert(htmlStr.contains("review-status-in-progress"), "Should contain status badge")
    assert(!htmlStr.contains("Phase "), "Should not contain phase")
    assert(!htmlStr.contains("review-message"), "Should not contain message")

  // Error Handling Tests (Phase 6)
  // Note: These tests will fail until we change the type from Option[ReviewState] to Option[Either[String, ReviewState]]

  test("render with None shows no review section (error handling)"):
    // This test verifies that None (no review state file) doesn't show review section
    val worktreesWithData = List(
      (sampleWorktree, Some((sampleIssueData, false, false)), None, None, None, None)
    )

    val html = WorktreeListView.render(worktreesWithData, now, "localhost")
    val htmlStr = html.render

    // Should not show review section
    assert(!htmlStr.contains("Review Artifacts"), "Should not show Review Artifacts heading")
    assert(!htmlStr.contains("review-artifacts"), "Should not contain review-artifacts class")
    assert(!htmlStr.contains("review-error"), "Should not contain review-error class")

  test("render with Some(Left(error)) shows error message"):
    val reviewState: Option[Either[String, ReviewState]] = Some(Left("Failed to parse review state JSON: unexpected token"))
    val worktreesWithData = List(
      (sampleWorktree, Some((sampleIssueData, false, false)), None, None, None, reviewState)
    )
    val html = WorktreeListView.render(worktreesWithData, now, "localhost")
    val htmlStr = html.render

    assert(htmlStr.contains("review-error"), "Should contain review-error class")
    assert(htmlStr.contains("Review state unavailable"), "Should show error message")
    assert(htmlStr.contains("review-error-message"), "Should have error message class")
    assert(htmlStr.contains("review-error-detail"), "Should have error detail class")

  test("render error message has correct CSS classes"):
    val reviewState: Option[Either[String, ReviewState]] = Some(Left("Some error"))
    val worktreesWithData = List(
      (sampleWorktree, Some((sampleIssueData, false, false)), None, None, None, reviewState)
    )
    val html = WorktreeListView.render(worktreesWithData, now, "localhost")
    val htmlStr = html.render

    // Verify all required CSS classes are present
    assert(htmlStr.contains("review-error"), "Should contain review-error class for container")
    assert(htmlStr.contains("review-error-message"), "Should contain review-error-message class for main message")
    assert(htmlStr.contains("review-error-detail"), "Should contain review-error-detail class for detail text")

  test("render with Some(Right(state)) and artifacts shows artifact list"):
    // This test verifies that Some(Right(state)) with artifacts shows the list
    val reviewState: Option[Either[String, ReviewState]] = Some(Right(ReviewState(
      status = Some("awaiting_review"),
      phase = Some(3),
      message = Some("Ready for review"),
      artifacts = List(
        ReviewArtifact("Analysis", "analysis.md"),
        ReviewArtifact("Context", "context.md")
      )
    )))
    val worktreesWithData = List(
      (sampleWorktree, Some((sampleIssueData, false, false)), None, None, None, reviewState)
    )
    val html = WorktreeListView.render(worktreesWithData, now, "localhost")
    val htmlStr = html.render

    assert(htmlStr.contains("review-artifacts"), "Should contain review-artifacts class")
    assert(htmlStr.contains("artifact-list"), "Should contain artifact-list class")
    assert(htmlStr.contains("Analysis"), "Should contain first artifact label")
    assert(htmlStr.contains("Context"), "Should contain second artifact label")

  test("render with Some(Right(state)) and empty artifacts shows nothing"):
    // This test verifies Some(Right(state)) with empty artifacts list shows nothing
    val reviewState: Option[Either[String, ReviewState]] = Some(Right(ReviewState(
      status = Some("awaiting_review"),
      phase = Some(3),
      message = Some("Ready for review"),
      artifacts = List.empty // Empty artifacts list
    )))
    val worktreesWithData = List(
      (sampleWorktree, Some((sampleIssueData, false, false)), None, None, None, reviewState)
    )
    val html = WorktreeListView.render(worktreesWithData, now, "localhost")
    val htmlStr = html.render

    assert(!htmlStr.contains("review-artifacts"), "Should not contain review-artifacts class")
    assert(!htmlStr.contains("artifact-list"), "Should not contain artifact-list class")

  test("Error message does not leak filesystem paths"):
    // Verify that error messages in UI don't expose sensitive filesystem paths
    // The error from service contains path info, but UI shows generic message
    val errorWithPath: Option[Either[String, ReviewState]] = Some(Left("Failed to parse /home/user/secret/review-state.json: syntax error"))
    val worktreesWithData = List(
      (sampleWorktree, Some((sampleIssueData, false, false)), None, None, None, errorWithPath)
    )
    val html = WorktreeListView.render(worktreesWithData, now, "localhost")
    val htmlStr = html.render

    // Should show error container with generic message
    assert(htmlStr.contains("review-error"), "Should show error container")
    assert(htmlStr.contains("Review state unavailable"), "Should show generic error message")

    // Should NOT leak the filesystem path from the error
    assert(!htmlStr.contains("/home/user/secret"), "Should not leak filesystem paths in HTML")
    assert(!htmlStr.contains("Failed to parse /home"), "Should not expose raw error message")

  // Zed Button Tests (IW-74 Phase 2)

  test("WorktreeListView renders Zed button in worktree card"):
    val worktreesWithData = List(
      (sampleWorktree, Some((sampleIssueData, false, false)), None, None, None, None)
    )

    val html = WorktreeListView.render(worktreesWithData, now, "dev-server")
    val htmlStr = html.render

    assert(htmlStr.contains("zed-button"), s"Should contain 'zed-button' class")
    assert(htmlStr.contains("zed://"), s"Should contain 'zed://' protocol")

  test("WorktreeListView Zed button has correct href format"):
    val worktree = sampleWorktree.copy(path = "/home/user/projects/my-project")
    val worktreesWithData = List(
      (worktree, Some((sampleIssueData, false, false)), None, None, None, None)
    )

    val html = WorktreeListView.render(worktreesWithData, now, "my-ssh-host")
    val htmlStr = html.render

    assert(htmlStr.contains("zed://ssh/my-ssh-host/home/user/projects/my-project"),
      s"Should contain correct Zed URL with SSH host and path")

  test("WorktreeListView Zed button has tooltip"):
    val worktreesWithData = List(
      (sampleWorktree, Some((sampleIssueData, false, false)), None, None, None, None)
    )

    val html = WorktreeListView.render(worktreesWithData, now, "test-server")
    val htmlStr = html.render

    assert(htmlStr.contains("Open in Zed"), s"Should contain 'Open in Zed' tooltip")

  // Stale Indicator Tests (Phase 1 - IW-92)

  test("WorktreeListView renders stale indicator when isStale flag is true"):
    val staleIssueData = (sampleIssueData, false, true) // (data, fromCache, isStale)
    val worktreesWithData = List(
      (sampleWorktree, Some(staleIssueData), None, None, None, None)
    )

    val html = WorktreeListView.render(worktreesWithData, now, "test-server")
    val htmlStr = html.render

    assert(htmlStr.contains("stale-indicator"), "Should contain stale-indicator class")
    assert(htmlStr.contains("stale") || htmlStr.contains("outdated"), "Should show stale indicator text")

  test("WorktreeListView does NOT render stale indicator when isStale flag is false"):
    val freshIssueData = (sampleIssueData, false, false) // (data, fromCache, isStale)
    val worktreesWithData = List(
      (sampleWorktree, Some(freshIssueData), None, None, None, None)
    )

    val html = WorktreeListView.render(worktreesWithData, now, "test-server")
    val htmlStr = html.render

    assert(!htmlStr.contains("stale-indicator"), "Should NOT contain stale-indicator class for fresh data")

  // Skeleton Card Tests (Phase 1 - IW-92)

  test("WorktreeListView renders skeleton card when issueData is None"):
    val worktreesWithData = List(
      (sampleWorktree, None, None, None, None, None)
    )

    val html = WorktreeListView.render(worktreesWithData, now, "test-server")
    val htmlStr = html.render

    assert(htmlStr.contains("skeleton-card"), "Should contain skeleton-card class when no issue data")
    assert(htmlStr.contains(sampleWorktree.issueId), "Should still show issue ID")

  test("skeleton card shows placeholder content"):
    val worktreesWithData = List(
      (sampleWorktree, None, None, None, None, None)
    )

    val html = WorktreeListView.render(worktreesWithData, now, "test-server")
    val htmlStr = html.render

    // Skeleton card should show the issue ID but not actual title
    assert(htmlStr.contains("IWLE-123"), "Should show issue ID")
    assert(!htmlStr.contains("Test Issue"), "Should not show actual issue title from test data")
    // Should show loading placeholder
    assert(htmlStr.contains("Loading") || htmlStr.contains("skeleton"), "Should indicate loading state")

  // HTMX Attributes Tests (Phase 4 - IW-92)

  test("Cards have refresh from:body in hx-trigger attribute"):
    val worktreesWithData = List(
      (sampleWorktree, Some((sampleIssueData, false, false)), None, None, None, None)
    )

    val html = WorktreeListView.render(worktreesWithData, now, "test-server")
    val htmlStr = html.render

    assert(htmlStr.contains("hx-trigger"), "Should contain hx-trigger attribute")
    assert(htmlStr.contains("refresh from:body"), "Should include 'refresh from:body' in hx-trigger")

  test("Cards have hx-swap with transition modifier"):
    val worktreesWithData = List(
      (sampleWorktree, Some((sampleIssueData, false, false)), None, None, None, None)
    )

    val html = WorktreeListView.render(worktreesWithData, now, "test-server")
    val htmlStr = html.render

    assert(htmlStr.contains("hx-swap"), "Should contain hx-swap attribute")
    assert(htmlStr.contains("outerHTML"), "Should use outerHTML swap strategy")
    assert(htmlStr.contains("transition:true"), "Should include transition:true modifier")

  // Staggered Polling Tests (Phase 5 - IW-92)

  test("First 3 skeleton cards have delay:500ms in hx-trigger"):
    val worktree1 = sampleWorktree.copy(issueId = "IW-1", path = "/path1")
    val worktree2 = sampleWorktree.copy(issueId = "IW-2", path = "/path2")
    val worktree3 = sampleWorktree.copy(issueId = "IW-3", path = "/path3")

    // Use None for issue data to render skeleton cards (which have staggered delays)
    val worktreesWithoutData = List(
      (worktree1, None, None, None, None, None),
      (worktree2, None, None, None, None, None),
      (worktree3, None, None, None, None, None)
    )

    val html = WorktreeListView.render(worktreesWithoutData, now, "test-server")
    val htmlStr = html.render

    // Extract each card's hx-trigger attribute
    val iw1Card = htmlStr.substring(htmlStr.indexOf("IW-1"), htmlStr.indexOf("IW-2"))
    val iw2Card = htmlStr.substring(htmlStr.indexOf("IW-2"), htmlStr.indexOf("IW-3"))
    val iw3Card = htmlStr.substring(htmlStr.indexOf("IW-3"), htmlStr.length)

    assert(iw1Card.contains("delay:500ms"), "Skeleton card 1 should have delay:500ms")
    assert(iw2Card.contains("delay:500ms"), "Skeleton card 2 should have delay:500ms")
    assert(iw3Card.contains("delay:500ms"), "Skeleton card 3 should have delay:500ms")

  test("Skeleton cards 4-8 have delay:2s in hx-trigger"):
    // Use None for issue data to render skeleton cards (which have staggered delays)
    val worktrees = (1 to 8).map { i =>
      (sampleWorktree.copy(issueId = s"IW-$i", path = s"/path$i"), None, None, None, None, None)
    }.toList

    val html = WorktreeListView.render(worktrees, now, "test-server")
    val htmlStr = html.render

    // Check skeleton cards 4-8 have delay:2s
    for (i <- 4 to 8) {
      val cardStart = htmlStr.indexOf(s"IW-$i")
      val cardEnd = if (i < 8) htmlStr.indexOf(s"IW-${i+1}") else htmlStr.length
      val card = htmlStr.substring(cardStart, cardEnd)
      assert(card.contains("delay:2s"), s"Skeleton card $i should have delay:2s")
    }

  test("Skeleton cards 9+ have delay:5s in hx-trigger"):
    // Use None for issue data to render skeleton cards (which have staggered delays)
    val worktrees = (1 to 12).map { i =>
      (sampleWorktree.copy(issueId = s"IW-$i", path = s"/path$i"), None, None, None, None, None)
    }.toList

    val html = WorktreeListView.render(worktrees, now, "test-server")
    val htmlStr = html.render

    // Check skeleton cards 9+ have delay:5s
    for (i <- 9 to 12) {
      val cardStart = htmlStr.indexOf(s"IW-$i")
      val cardEnd = if (i < 12) htmlStr.indexOf(s"IW-${i+1}") else htmlStr.length
      val card = htmlStr.substring(cardStart, cardEnd)
      assert(card.contains("delay:5s"), s"Skeleton card $i should have delay:5s")
    }

  test("Staggered delays with single skeleton worktree"):
    // Use None for issue data to render skeleton card
    val worktreesWithoutData = List(
      (sampleWorktree, None, None, None, None, None)
    )

    val html = WorktreeListView.render(worktreesWithoutData, now, "test-server")
    val htmlStr = html.render

    assert(htmlStr.contains("delay:500ms"), "Single skeleton card should have delay:500ms (position 1)")

  test("Data cards do not have load in hx-trigger (prevents infinite loop)"):
    val worktreesWithData = List(
      (sampleWorktree, Some((sampleIssueData, false, false)), None, None, None, None)
    )

    val html = WorktreeListView.render(worktreesWithData, now, "test-server")
    val htmlStr = html.render

    // Data cards should have hx-trigger but NOT with 'load' to prevent infinite refresh loop
    assert(htmlStr.contains("hx-trigger"), "Data card should have hx-trigger")
    assert(htmlStr.contains("every 30s"), "Data card should have periodic refresh")
    assert(!htmlStr.contains("hx-trigger=\"load"), "Data card should NOT have 'load' trigger (prevents infinite loop)")
