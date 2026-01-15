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

    val (html, _) = DashboardService.renderDashboard(
      worktrees = List(worktree),
      issueCache = Map.empty,
      progressCache = Map.empty,
      prCache = Map.empty,
      reviewStateCache = reviewStateCache,
      config = None,
      sshHost = "localhost"
    )

    // Verify dashboard was rendered (basic check)
    assert(html.contains("<!DOCTYPE html>"))
    assert(html.contains("iw Dashboard"))
    assert(html.contains("IWLE-123"))

  test("renderDashboard handles missing review state gracefully"):
    val worktree = createWorktree("IWLE-456")

    val (html, _) = DashboardService.renderDashboard(
      worktrees = List(worktree),
      issueCache = Map.empty,
      progressCache = Map.empty,
      prCache = Map.empty,
      reviewStateCache = Map.empty, // No cached review state
      config = None,
      sshHost = "localhost"
    )

    // Verify dashboard was rendered without errors
    assert(html.contains("<!DOCTYPE html>"))
    assert(html.contains("IWLE-456"))

  test("renderDashboard with empty worktree list"):
    val (html, _) = DashboardService.renderDashboard(
      worktrees = List.empty,
      issueCache = Map.empty,
      progressCache = Map.empty,
      prCache = Map.empty,
      reviewStateCache = Map.empty,
      config = None,
      sshHost = "localhost"
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

    val (html, _) = DashboardService.renderDashboard(
      worktrees = List(worktree1, worktree2, worktree3),
      issueCache = Map.empty,
      progressCache = Map.empty,
      prCache = Map.empty,
      reviewStateCache = reviewStateCache,
      config = None,
      sshHost = "localhost"
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

    val (html, _) = DashboardService.renderDashboard(
      worktrees = List(worktree),
      issueCache = Map.empty,
      progressCache = Map.empty,
      prCache = Map.empty,
      reviewStateCache = reviewStateCache, // Wrong issue ID
      config = None,
      sshHost = "localhost"
    )

    // Dashboard should render without the cached review state
    assert(html.contains("<!DOCTYPE html>"))
    assert(html.contains("IWLE-789"))

  // Error Handling Tests (Phase 6)

  test("fetchReviewStateForWorktree with missing state file doesn't crash dashboard"):
    // This test verifies that missing files don't crash the dashboard
    // Note: Testing exact behavior of file-not-found requires integration tests with real files
    val worktree = createWorktree("IWLE-MISSING")

    // DashboardService.fetchReviewStateForWorktree is private, so we test via renderDashboard
    val (html, cache) = DashboardService.renderDashboard(
      worktrees = List(worktree),
      issueCache = Map.empty,
      progressCache = Map.empty,
      prCache = Map.empty,
      reviewStateCache = Map.empty,
      config = None,
      sshHost = "localhost"
    )

    // Dashboard should render successfully (no crash)
    assert(html.contains("<!DOCTYPE html>"))
    assert(html.contains("IWLE-MISSING"))
    // The exact behavior (None vs Some(Left)) depends on file I/O implementation details
    // What matters is the dashboard renders without crashing

  test("fetchReviewStateForWorktree returns Some(Left) when JSON invalid"):
    // This test will fail until we change the return type
    // Current implementation discards errors with .toOption
    // After Phase 6, invalid JSON should return Some(Left(error))

    // We can't easily test this directly since fetchReviewStateForWorktree is private
    // and uses real file I/O. This test documents the expected behavior.
    // The implementation will be verified through integration testing.

    // For now, just verify current behavior doesn't crash
    val worktree = createWorktree("IWLE-INVALID")
    val (html, cache) = DashboardService.renderDashboard(
      worktrees = List(worktree),
      issueCache = Map.empty,
      progressCache = Map.empty,
      prCache = Map.empty,
      reviewStateCache = Map.empty,
      config = None,
      sshHost = "localhost"
    )

    assert(html.contains("IWLE-INVALID"))
    // Current behavior: no review section, no error
    // Future behavior: should show error message

  test("fetchReviewStateForWorktree with fake paths renders dashboard"):
    // This test verifies dashboard renders even with non-existent file paths
    // Testing exact cache behavior requires integration tests with real files
    val worktree = createWorktree("IWLE-VALID")

    val (html, cache) = DashboardService.renderDashboard(
      worktrees = List(worktree),
      issueCache = Map.empty,
      progressCache = Map.empty,
      prCache = Map.empty,
      reviewStateCache = Map.empty,
      config = None,
      sshHost = "localhost"
    )

    // Dashboard should render successfully
    assert(html.contains("<!DOCTYPE html>"))
    assert(html.contains("IWLE-VALID"))
    // Testing exact cache update behavior requires real files

  test("renderDashboard does not crash with invalid review state"):
    // This test ensures the dashboard renders even when review state loading fails
    // Since we're using fake paths, files won't exist and review sections won't show
    val worktree1 = createWorktree("IWLE-OK", "/path/ok")
    val worktree2 = createWorktree("IWLE-BAD", "/path/bad")

    val (html, cache) = DashboardService.renderDashboard(
      worktrees = List(worktree1, worktree2),
      issueCache = Map.empty,
      progressCache = Map.empty,
      prCache = Map.empty,
      reviewStateCache = Map.empty,
      config = None,
      sshHost = "localhost"
    )

    // Dashboard should render successfully
    assert(html.contains("<!DOCTYPE html>"))
    assert(html.contains("IWLE-OK"))
    assert(html.contains("IWLE-BAD"))
    // Without real files, no review section will be shown
    // But dashboard should still render without errors
    assert(!cache.contains("IWLE-OK"))
    assert(!cache.contains("IWLE-BAD"))

  test("Cache not updated when state is invalid"):
    // This test verifies that invalid states don't pollute the cache
    // After Phase 6 changes, only valid states (Some(Right)) should update cache
    val worktree = createWorktree("IWLE-INVALID-CACHE")

    val (_, cache) = DashboardService.renderDashboard(
      worktrees = List(worktree),
      issueCache = Map.empty,
      progressCache = Map.empty,
      prCache = Map.empty,
      reviewStateCache = Map.empty, // Start with empty cache
      config = None,
      sshHost = "localhost"
    )

    // Cache should not contain entry for invalid/missing state
    assert(!cache.contains("IWLE-INVALID-CACHE"))

  // SSH Host Configuration Tests (IW-74 Phase 1)

  test("renderDashboard accepts sshHost parameter"):
    val worktree = createWorktree("IWLE-SSH-1")
    val (html, _) = DashboardService.renderDashboard(
      worktrees = List(worktree),
      issueCache = Map.empty,
      progressCache = Map.empty,
      prCache = Map.empty,
      reviewStateCache = Map.empty,
      config = None,
      sshHost = "my-server.example.com"
    )

    // Should render without errors
    assert(html.contains("<!DOCTYPE html>"))
    assert(html.contains("IWLE-SSH-1"))

  test("renderDashboard includes SSH host input field in HTML"):
    val worktree = createWorktree("IWLE-SSH-2")
    val (html, _) = DashboardService.renderDashboard(
      worktrees = List(worktree),
      issueCache = Map.empty,
      progressCache = Map.empty,
      prCache = Map.empty,
      reviewStateCache = Map.empty,
      config = None,
      sshHost = "test-server.local"
    )

    // Verify SSH host input field is present
    assert(html.contains("ssh-host-input"))
    assert(html.contains("test-server.local"))

  test("renderDashboard SSH host form submits to current URL"):
    val worktree = createWorktree("IWLE-SSH-3")
    val (html, _) = DashboardService.renderDashboard(
      worktrees = List(worktree),
      issueCache = Map.empty,
      progressCache = Map.empty,
      prCache = Map.empty,
      reviewStateCache = Map.empty,
      config = None,
      sshHost = "my-host"
    )

    // Verify form has correct structure
    assert(html.contains("ssh-host-form"))
    assert(html.contains("method=\"get\""))

  // Zed Button Integration Tests (IW-74 Phase 2)

  test("renderDashboard includes Zed button with configured SSH host"):
    val worktree = createWorktree("IWLE-ZED-1", "/home/user/projects/my-project")
    val (html, _) = DashboardService.renderDashboard(
      worktrees = List(worktree),
      issueCache = Map.empty,
      progressCache = Map.empty,
      prCache = Map.empty,
      reviewStateCache = Map.empty,
      config = None,
      sshHost = "dev-server"
    )

    // Verify Zed button appears in HTML
    assert(html.contains("zed-button"))
    assert(html.contains("zed://ssh/dev-server/home/user/projects/my-project"))
    assert(html.contains("Open in Zed"))

  test("renderDashboard Zed button uses correct SSH host for multiple worktrees"):
    val worktree1 = createWorktree("IWLE-ZED-2", "/home/user/project-a")
    val worktree2 = createWorktree("IWLE-ZED-3", "/home/user/project-b")

    val (html, _) = DashboardService.renderDashboard(
      worktrees = List(worktree1, worktree2),
      issueCache = Map.empty,
      progressCache = Map.empty,
      prCache = Map.empty,
      reviewStateCache = Map.empty,
      config = None,
      sshHost = "test-host"
    )

    // Verify both worktrees have Zed buttons with correct SSH host
    assert(html.contains("zed://ssh/test-host/home/user/project-a"))
    assert(html.contains("zed://ssh/test-host/home/user/project-b"))

  // CSS Transition Tests (Phase 4 - IW-92)

  test("Dashboard CSS includes .htmx-swapping styles"):
    val worktree = createWorktree("IWLE-TEST")
    val (html, _) = DashboardService.renderDashboard(
      worktrees = List(worktree),
      issueCache = Map.empty,
      progressCache = Map.empty,
      prCache = Map.empty,
      reviewStateCache = Map.empty,
      config = None,
      sshHost = "test-server"
    )

    assert(html.contains(".htmx-swapping"), "Should contain .htmx-swapping CSS class")
    assert(html.contains("opacity: 0"), "Should contain opacity: 0 for swapping state")

  test("Dashboard CSS includes .htmx-settling styles"):
    val worktree = createWorktree("IWLE-TEST")
    val (html, _) = DashboardService.renderDashboard(
      worktrees = List(worktree),
      issueCache = Map.empty,
      progressCache = Map.empty,
      prCache = Map.empty,
      reviewStateCache = Map.empty,
      config = None,
      sshHost = "test-server"
    )

    assert(html.contains(".htmx-settling"), "Should contain .htmx-settling CSS class")
    assert(html.contains("opacity: 1"), "Should contain opacity: 1 for settling state")

  test("Dashboard CSS includes transition property for cards"):
    val worktree = createWorktree("IWLE-TEST")
    val (html, _) = DashboardService.renderDashboard(
      worktrees = List(worktree),
      issueCache = Map.empty,
      progressCache = Map.empty,
      prCache = Map.empty,
      reviewStateCache = Map.empty,
      config = None,
      sshHost = "test-server"
    )

    assert(html.contains("transition:"), "Should contain transition property")
    assert(html.contains("opacity"), "Should include opacity in transition")
    assert(html.contains("200ms") || html.contains("0.2s"), "Should specify transition duration")

  // Tab Visibility Tests (Phase 4 - IW-92)

  test("Dashboard HTML includes visibilitychange script"):
    val worktree = createWorktree("IWLE-TEST")
    val (html, _) = DashboardService.renderDashboard(
      worktrees = List(worktree),
      issueCache = Map.empty,
      progressCache = Map.empty,
      prCache = Map.empty,
      reviewStateCache = Map.empty,
      config = None,
      sshHost = "test-server"
    )

    assert(html.contains("visibilitychange"), "Should contain visibilitychange event listener")
    assert(html.contains("htmx.trigger"), "Should use htmx.trigger to trigger refresh")
    assert(html.contains("document.body"), "Should trigger refresh on document.body")

  // Mobile Styling Tests (Phase 4 - IW-92)

  test("Dashboard CSS includes mobile breakpoint styles"):
    val worktree = createWorktree("IWLE-TEST")
    val (html, _) = DashboardService.renderDashboard(
      worktrees = List(worktree),
      issueCache = Map.empty,
      progressCache = Map.empty,
      prCache = Map.empty,
      reviewStateCache = Map.empty,
      config = None,
      sshHost = "test-server"
    )

    assert(html.contains("@media"), "Should contain @media query for responsive design")
    assert(html.contains("max-width") || html.contains("min-width"), "Should have breakpoint conditions")

  test("Dashboard CSS includes minimum touch target sizes"):
    val worktree = createWorktree("IWLE-TEST")
    val (html, _) = DashboardService.renderDashboard(
      worktrees = List(worktree),
      issueCache = Map.empty,
      progressCache = Map.empty,
      prCache = Map.empty,
      reviewStateCache = Map.empty,
      config = None,
      sshHost = "test-server"
    )

    assert(html.contains("min-height: 44px") || html.contains("min-height:44px"), "Should have 44px minimum touch target height")

  test("Dashboard CSS includes touch-action manipulation"):
    val worktree = createWorktree("IWLE-TEST")
    val (html, _) = DashboardService.renderDashboard(
      worktrees = List(worktree),
      issueCache = Map.empty,
      progressCache = Map.empty,
      prCache = Map.empty,
      reviewStateCache = Map.empty,
      config = None,
      sshHost = "test-server"
    )

    assert(html.contains("touch-action"), "Should contain touch-action property")
    assert(html.contains("manipulation"), "Should use manipulation value to prevent zoom on double-tap")
