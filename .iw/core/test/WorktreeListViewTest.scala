// PURPOSE: Unit tests for WorktreeListView rendering
// PURPOSE: Verify HTML output for worktree cards including review artifacts section

package iw.core.test

import iw.core.domain.{WorktreeRegistration, IssueData, WorkflowProgress, GitStatus, PullRequestData, ReviewState, ReviewArtifact}
import iw.core.presentation.views.WorktreeListView
import java.time.Instant

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
    val reviewState = Some(ReviewState(
      status = Some("awaiting_review"),
      phase = Some(1),
      message = Some("Ready for review"),
      artifacts = List(
        ReviewArtifact("Analysis", "project-management/issues/IWLE-123/analysis.md"),
        ReviewArtifact("Context", "project-management/issues/IWLE-123/phase-01-context.md")
      )
    ))

    val worktreesWithData = List(
      (sampleWorktree, Some((sampleIssueData, false)), None, None, None, reviewState)
    )

    val html = WorktreeListView.render(worktreesWithData, now)
    val htmlStr = html.render

    assert(htmlStr.contains("Review Artifacts"), s"Should contain 'Review Artifacts' heading")
    assert(htmlStr.contains("review-artifacts"), s"Should contain 'review-artifacts' class")
    assert(htmlStr.contains("artifact-list"), s"Should contain 'artifact-list' class")
    assert(htmlStr.contains("Analysis"), s"Should contain artifact label 'Analysis'")
    assert(htmlStr.contains("Context"), s"Should contain artifact label 'Context'")

  test("WorktreeListView omits review section when reviewState is None"):
    val worktreesWithData = List(
      (sampleWorktree, Some((sampleIssueData, false)), None, None, None, None)
    )

    val html = WorktreeListView.render(worktreesWithData, now)
    val htmlStr = html.render

    assert(!htmlStr.contains("Review Artifacts"), s"Should not contain 'Review Artifacts' when reviewState is None")
    assert(!htmlStr.contains("review-artifacts"), s"Should not contain 'review-artifacts' class")
    assert(!htmlStr.contains("artifact-list"), s"Should not contain 'artifact-list' class")

  test("WorktreeListView omits review section when artifacts list is empty"):
    val reviewState = Some(ReviewState(
      status = Some("awaiting_review"),
      phase = Some(1),
      message = Some("Ready for review"),
      artifacts = List.empty  // Empty artifacts list
    ))

    val worktreesWithData = List(
      (sampleWorktree, Some((sampleIssueData, false)), None, None, None, reviewState)
    )

    val html = WorktreeListView.render(worktreesWithData, now)
    val htmlStr = html.render

    assert(!htmlStr.contains("Review Artifacts"), s"Should not contain 'Review Artifacts' when artifacts list is empty")
    assert(!htmlStr.contains("review-artifacts"), s"Should not contain 'review-artifacts' class")

  test("WorktreeListView displays all artifact labels correctly"):
    val reviewState = Some(ReviewState(
      status = None,
      phase = None,
      message = None,
      artifacts = List(
        ReviewArtifact("Analysis Doc", "path/to/analysis.md"),
        ReviewArtifact("Phase Context", "path/to/context.md"),
        ReviewArtifact("Review Packet", "path/to/review-packet.md")
      )
    ))

    val worktreesWithData = List(
      (sampleWorktree, Some((sampleIssueData, false)), None, None, None, reviewState)
    )

    val html = WorktreeListView.render(worktreesWithData, now)
    val htmlStr = html.render

    assert(htmlStr.contains("Analysis Doc"), s"Should contain artifact label 'Analysis Doc'")
    assert(htmlStr.contains("Phase Context"), s"Should contain artifact label 'Phase Context'")
    assert(htmlStr.contains("Review Packet"), s"Should contain artifact label 'Review Packet'")
    // Verify artifacts are clickable links
    assert(htmlStr.contains("<a"), s"Artifacts should be links")
    assert(htmlStr.contains("/worktrees/"), s"Artifact links should point to worktree artifacts route")
    assert(htmlStr.contains("?path="), s"Artifact links should include path query parameter")

  test("WorktreeListView renders empty state when no worktrees"):
    val html = WorktreeListView.render(List.empty, now)
    val htmlStr = html.render

    assert(htmlStr.contains("empty-state"), s"Should contain 'empty-state' class")
    assert(htmlStr.contains("No worktrees registered yet"), s"Should show empty state message")

  test("WorktreeListView renders multiple worktrees with different review states"):
    val wt1 = sampleWorktree.copy(issueId = "IWLE-1")
    val wt2 = sampleWorktree.copy(issueId = "IWLE-2")

    val reviewState1 = Some(ReviewState(None, None, None, List(ReviewArtifact("Doc1", "doc1.md"))))
    val reviewState2 = None  // No review state

    val worktreesWithData = List(
      (wt1, Some((sampleIssueData, false)), None, None, None, reviewState1),
      (wt2, Some((sampleIssueData, false)), None, None, None, reviewState2)
    )

    val html = WorktreeListView.render(worktreesWithData, now)
    val htmlStr = html.render

    // Should have one review section (for wt1) but not for wt2
    assert(htmlStr.contains("Doc1"), s"Should contain artifact for first worktree")
    // Count occurrences of "Review Artifacts" - should be exactly 1
    val reviewArtifactsCount = "Review Artifacts".r.findAllIn(htmlStr).length
    assertEquals(reviewArtifactsCount, 1, s"Should have exactly one 'Review Artifacts' section")
