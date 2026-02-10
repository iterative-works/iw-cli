// PURPOSE: Unit tests for WorktreeListView rendering
// PURPOSE: Verify HTML output for worktree cards including review artifacts section

package iw.core.test

import iw.core.model.{WorktreeRegistration, IssueData, WorkflowProgress, GitStatus, PullRequestData, ReviewState, ReviewArtifact, Display, Badge, TaskList}
import iw.core.dashboard.WorktreeListView
import java.time.Instant
import iw.core.model.Check
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
      display = None,
      badges = None,
      taskLists = None,
      needsAttention = None,
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
      display = None,
      badges = None,
      taskLists = None,
      needsAttention = None,
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
      display = None,
      badges = None,
      taskLists = None,
      needsAttention = None,
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

    val reviewState1 = Some(Right(ReviewState(
      display = None,
      badges = None,
      taskLists = None,
      needsAttention = None,
      message = None,
      artifacts = List(ReviewArtifact("Doc1", "doc1.md")))))
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

  // Display Badge Rendering Tests

  test("renderWorktreeCard includes display badge when display is defined"):
    val reviewState = Some(Right(ReviewState(
      display = Some(Display("Implementing", Some("Phase 2 of 3"), "progress")),
      badges = None,
      taskLists = None,
      needsAttention = None,
      message = None,
      artifacts = List(ReviewArtifact("Analysis", "analysis.md"))
    )))

    val worktreesWithData = List(
      (sampleWorktree, Some((sampleIssueData, false, false)), None, None, None, reviewState)
    )

    val html = WorktreeListView.render(worktreesWithData, now, "localhost")
    val htmlStr = html.render

    assert(htmlStr.contains("review-status"), "Should contain review-status class")
    assert(htmlStr.contains("display-type-progress"), "Should contain display type CSS class")
    assert(htmlStr.contains("Implementing"), "Should contain display text")
    assert(htmlStr.contains("Phase 2 of 3"), "Should contain display subtext")

  test("renderWorktreeCard includes display badge with correct class for success type"):
    val reviewState = Some(Right(ReviewState(
      display = Some(Display("Complete", None, "success")),
      badges = None,
      taskLists = None,
      needsAttention = None,
      message = None,
      artifacts = List(ReviewArtifact("Analysis", "analysis.md"))
    )))

    val worktreesWithData = List(
      (sampleWorktree, Some((sampleIssueData, false, false)), None, None, None, reviewState)
    )

    val html = WorktreeListView.render(worktreesWithData, now, "localhost")
    val htmlStr = html.render

    assert(htmlStr.contains("display-type-success"), "Should contain success display type class")
    assert(htmlStr.contains("Complete"), "Should contain Complete label")

  test("renderWorktreeCard includes display badge with correct class for warning type"):
    val reviewState = Some(Right(ReviewState(
      display = Some(Display("Awaiting Review", Some("Phase 3 of 4"), "warning")),
      badges = None,
      taskLists = None,
      needsAttention = None,
      message = None,
      artifacts = List(ReviewArtifact("Analysis", "analysis.md"))
    )))

    val worktreesWithData = List(
      (sampleWorktree, Some((sampleIssueData, false, false)), None, None, None, reviewState)
    )

    val html = WorktreeListView.render(worktreesWithData, now, "localhost")
    val htmlStr = html.render

    assert(htmlStr.contains("display-type-warning"), "Should contain warning display type class")
    assert(htmlStr.contains("Awaiting Review"), "Should contain Awaiting Review label")

  test("renderWorktreeCard omits display badge when display is None"):
    val reviewState = Some(Right(ReviewState(
      display = None,
      badges = None,
      taskLists = None,
      needsAttention = None,
      message = None,
      artifacts = List(ReviewArtifact("Analysis", "analysis.md"))
    )))

    val worktreesWithData = List(
      (sampleWorktree, Some((sampleIssueData, false, false)), None, None, None, reviewState)
    )

    val html = WorktreeListView.render(worktreesWithData, now, "localhost")
    val htmlStr = html.render

    assert(!htmlStr.contains("display-type-"), "Should not contain display type classes when display is None")
    assert(!htmlStr.contains("review-status-label"), "Should not contain status label when display is None")

  // Badges Array Rendering Tests

  test("renderWorktreeCard renders badges array when badges are defined"):
    val reviewState = Some(Right(ReviewState(
      display = None,
      badges = Some(List(
        Badge("TDD", "success"),
        Badge("Batch", "info")
      )),
      taskLists = None,
      needsAttention = None,
      message = None,
      artifacts = List(ReviewArtifact("Analysis", "analysis.md"))
    )))

    val worktreesWithData = List(
      (sampleWorktree, Some((sampleIssueData, false, false)), None, None, None, reviewState)
    )

    val html = WorktreeListView.render(worktreesWithData, now, "localhost")
    val htmlStr = html.render

    assert(htmlStr.contains("review-badges"), "Should contain review-badges class")
    assert(htmlStr.contains("TDD"), "Should contain TDD badge label")
    assert(htmlStr.contains("Batch"), "Should contain Batch badge label")
    assert(htmlStr.contains("display-type-success"), "Should contain success type class for TDD")
    assert(htmlStr.contains("display-type-info"), "Should contain info type class for Batch")

  test("renderWorktreeCard omits badges section when badges is None"):
    val reviewState = Some(Right(ReviewState(
      display = None,
      badges = None,
      taskLists = None,
      needsAttention = None,
      message = None,
      artifacts = List(ReviewArtifact("Analysis", "analysis.md"))
    )))

    val worktreesWithData = List(
      (sampleWorktree, Some((sampleIssueData, false, false)), None, None, None, reviewState)
    )

    val html = WorktreeListView.render(worktreesWithData, now, "localhost")
    val htmlStr = html.render

    assert(!htmlStr.contains("review-badges"), "Should not contain review-badges class when badges is None")

  // Message Display Tests

  test("renderWorktreeCard includes message when message is defined"):
    val reviewState = Some(Right(ReviewState(
      display = None,
      badges = None,
      taskLists = None,
      needsAttention = None,
      message = Some("Phase 3 complete - Ready for review"),
      artifacts = List(ReviewArtifact("Analysis", "analysis.md"))
    )))

    val worktreesWithData = List(
      (sampleWorktree, Some((sampleIssueData, false, false)), None, None, None, reviewState)
    )

    val html = WorktreeListView.render(worktreesWithData, now, "localhost")
    val htmlStr = html.render

    assert(htmlStr.contains("review-message"), "Should contain review-message class")
    assert(htmlStr.contains("Phase 3 complete - Ready for review"), "Should contain message text")

  test("renderWorktreeCard omits message when message is None"):
    val reviewState = Some(Right(ReviewState(
      display = None,
      badges = None,
      taskLists = None,
      needsAttention = None,
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

  test("renderWorktreeCard displays display, badges, and message together"):
    val reviewState = Some(Right(ReviewState(
      display = Some(Display("In Review", Some("Phase 3 of 4"), "progress")),
      badges = Some(List(Badge("TDD", "success"))),
      taskLists = None,
      needsAttention = None,
      message = Some("Ready for review"),
      artifacts = List(ReviewArtifact("Analysis", "analysis.md"))
    )))

    val worktreesWithData = List(
      (sampleWorktree, Some((sampleIssueData, false, false)), None, None, None, reviewState)
    )

    val html = WorktreeListView.render(worktreesWithData, now, "localhost")
    val htmlStr = html.render

    assert(htmlStr.contains("display-type-progress"), "Should contain display badge")
    assert(htmlStr.contains("In Review"), "Should contain display text")
    assert(htmlStr.contains("Phase 3 of 4"), "Should contain display subtext")
    assert(htmlStr.contains("TDD"), "Should contain badge")
    assert(htmlStr.contains("Ready for review"), "Should contain message")

  test("renderWorktreeCard handles missing display, badges, and message gracefully"):
    val reviewState = Some(Right(ReviewState(
      display = None,
      badges = None,
      taskLists = None,
      needsAttention = None,
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

    // Should NOT render display/badges/message elements
    assert(!htmlStr.contains("display-type-"), "Should not render display badge")
    assert(!htmlStr.contains("review-badges"), "Should not render badges")
    assert(!htmlStr.contains("review-message"), "Should not render message")

  test("renderWorktreeCard displays partial fields correctly (only display)"):
    val reviewState = Some(Right(ReviewState(
      display = Some(Display("Implementing", None, "progress")),
      badges = None,
      taskLists = None,
      needsAttention = None,
      message = None,
      artifacts = List(ReviewArtifact("Analysis", "analysis.md"))
    )))

    val worktreesWithData = List(
      (sampleWorktree, Some((sampleIssueData, false, false)), None, None, None, reviewState)
    )

    val html = WorktreeListView.render(worktreesWithData, now, "localhost")
    val htmlStr = html.render

    assert(htmlStr.contains("display-type-progress"), "Should contain display badge")
    assert(htmlStr.contains("Implementing"), "Should contain display text")
    assert(!htmlStr.contains("review-badges"), "Should not render badges when None")
    assert(!htmlStr.contains("review-message"), "Should not render message when None")

  // Helper Function Tests

  test("displayTypeClass maps display types to CSS classes"):
    assertEquals(WorktreeListView.displayTypeClass("progress"), "display-type-progress")
    assertEquals(WorktreeListView.displayTypeClass("success"), "display-type-success")
    assertEquals(WorktreeListView.displayTypeClass("warning"), "display-type-warning")
    assertEquals(WorktreeListView.displayTypeClass("error"), "display-type-error")
    assertEquals(WorktreeListView.displayTypeClass("info"), "display-type-info")

  test("displayTypeClass handles uppercase input"):
    assertEquals(WorktreeListView.displayTypeClass("PROGRESS"), "display-type-progress")
    assertEquals(WorktreeListView.displayTypeClass("SUCCESS"), "display-type-success")

  test("displayTypeClass handles mixed case input"):
    assertEquals(WorktreeListView.displayTypeClass("Progress"), "display-type-progress")
    assertEquals(WorktreeListView.displayTypeClass("Warning"), "display-type-warning")
