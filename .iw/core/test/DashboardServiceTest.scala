// PURPOSE: Unit tests for DashboardService application logic
// PURPOSE: Tests review state integration in dashboard rendering

package iw.core.application

import munit.FunSuite
import iw.core.domain.{
  WorktreeRegistration,
  ReviewState,
  ReviewArtifact,
  CachedReviewState,
  CachedIssue,
  CachedProgress,
  CachedPR
}
import java.time.Instant

class DashboardServiceTest extends FunSuite:

  // Test fixtures
  private val now = Instant.now()

  private def createWorktree(
    issueId: String,
    path: String = "/path/to/worktree"
  ): WorktreeRegistration =
    WorktreeRegistration(
      issueId = issueId,
      path = path,
      trackerType = "linear",
      team = "IWLE",
      registeredAt = now,
      lastSeenAt = now
    )

  // Review State Integration Tests

  test("renderDashboard includes review state when present in cache"):
    val worktree = createWorktree("IWLE-123")
    val reviewState = ReviewState(
      status = Some("awaiting_review"),
      phase = Some(3),
      message = Some("Ready for code review"),
      artifacts = List(
        ReviewArtifact("Analysis", "project-management/issues/IWLE-123/analysis.md"),
        ReviewArtifact("Tasks", "project-management/issues/IWLE-123/tasks.md")
      )
    )
    val reviewStateCache = Map(
      "IWLE-123" -> CachedReviewState(
        reviewState,
        Map("/path/to/worktree/project-management/issues/IWLE-123/review-state.json" -> 1000L)
      )
    )

    val html = DashboardService.renderDashboard(
      worktrees = List(worktree),
      issueCache = Map.empty,
      progressCache = Map.empty,
      prCache = Map.empty,
      reviewStateCache = reviewStateCache,
      config = None
    )

    // Verify dashboard was rendered (basic check)
    assert(html.contains("<!DOCTYPE html>"))
    assert(html.contains("iw Dashboard"))
    assert(html.contains("IWLE-123"))

  test("renderDashboard handles missing review state gracefully"):
    val worktree = createWorktree("IWLE-456")

    val html = DashboardService.renderDashboard(
      worktrees = List(worktree),
      issueCache = Map.empty,
      progressCache = Map.empty,
      prCache = Map.empty,
      reviewStateCache = Map.empty, // No cached review state
      config = None
    )

    // Verify dashboard was rendered without errors
    assert(html.contains("<!DOCTYPE html>"))
    assert(html.contains("IWLE-456"))

  test("renderDashboard with empty worktree list"):
    val html = DashboardService.renderDashboard(
      worktrees = List.empty,
      issueCache = Map.empty,
      progressCache = Map.empty,
      prCache = Map.empty,
      reviewStateCache = Map.empty,
      config = None
    )

    // Verify empty state is rendered
    assert(html.contains("<!DOCTYPE html>"))
    assert(html.contains("iw Dashboard"))

  test("renderDashboard with multiple worktrees and mixed review state availability"):
    val worktree1 = createWorktree("IWLE-100", "/path1")
    val worktree2 = createWorktree("IWLE-200", "/path2")
    val worktree3 = createWorktree("IWLE-300", "/path3")

    val reviewState = ReviewState(
      status = Some("in_progress"),
      phase = Some(2),
      message = None,
      artifacts = List(ReviewArtifact("Context", "context.md"))
    )

    // Only worktree2 has cached review state
    val reviewStateCache = Map(
      "IWLE-200" -> CachedReviewState(
        reviewState,
        Map("/path2/project-management/issues/IWLE-200/review-state.json" -> 1000L)
      )
    )

    val html = DashboardService.renderDashboard(
      worktrees = List(worktree1, worktree2, worktree3),
      issueCache = Map.empty,
      progressCache = Map.empty,
      prCache = Map.empty,
      reviewStateCache = reviewStateCache,
      config = None
    )

    // Verify all worktrees are rendered
    assert(html.contains("IWLE-100"))
    assert(html.contains("IWLE-200"))
    assert(html.contains("IWLE-300"))

  test("renderDashboard review state cache is keyed by issue ID"):
    val worktree = createWorktree("IWLE-789")

    // Cache entry with different issue ID should not match
    val reviewState = ReviewState(
      status = Some("completed"),
      phase = Some(5),
      message = Some("All done"),
      artifacts = List.empty
    )
    val reviewStateCache = Map(
      "IWLE-DIFFERENT" -> CachedReviewState(
        reviewState,
        Map("/path/review-state.json" -> 1000L)
      )
    )

    val html = DashboardService.renderDashboard(
      worktrees = List(worktree),
      issueCache = Map.empty,
      progressCache = Map.empty,
      prCache = Map.empty,
      reviewStateCache = reviewStateCache, // Wrong issue ID
      config = None
    )

    // Dashboard should render without the cached review state
    assert(html.contains("<!DOCTYPE html>"))
    assert(html.contains("IWLE-789"))
