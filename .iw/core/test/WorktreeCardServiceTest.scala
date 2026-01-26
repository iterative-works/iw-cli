// PURPOSE: Unit tests for WorktreeCardService per-card rendering
// PURPOSE: Validates single card refresh with throttling and error handling

package iw.tests

import iw.core.dashboard.{WorktreeCardService, RefreshThrottle}
import iw.core.dashboard.CardRenderResult
import iw.core.model.{WorktreeRegistration, IssueData, CachedIssue}
import iw.core.model.{Issue, IssueId, ReviewState, ReviewArtifact, CachedReviewState}
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.nio.file.{Files, Path}

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

  // ============================================================================
  // Review State Regression Tests (IW-164)
  // ============================================================================
  // These tests document the working pattern for review state caching.
  // Review state works correctly because:
  // 1. WorktreeCardService.renderCard always calls fetchReviewStateForWorktree
  // 2. The fetched data is returned in CardRenderResult.fetchedReviewState
  // 3. CaskServer updates the cache from this returned value
  //
  // This pattern should be followed for progress and PR data (Stories 1-2).
  // ============================================================================

  test("renderCard returns fetchedReviewState when review-state.json exists"):
    // Create temporary directory with review-state.json
    val tempDir = Files.createTempDirectory("test-worktree")
    val issueDir = tempDir.resolve("project-management/issues/IW-TEST")
    Files.createDirectories(issueDir)
    val reviewStateFile = issueDir.resolve("review-state.json")
    val reviewStateJson = """{
      "status": "awaiting_review",
      "phase": 1,
      "message": "Ready for review",
      "artifacts": [
        {"label": "Analysis", "path": "analysis.md"}
      ]
    }"""
    Files.write(reviewStateFile, reviewStateJson.getBytes)

    try
      val now = Instant.now()
      val issueId = "IW-TEST"
      val worktree = WorktreeRegistration(
        issueId = issueId,
        path = tempDir.toString,
        trackerType = "Linear",
        team = "IW",
        registeredAt = now,
        lastSeenAt = now
      )
      val throttle = RefreshThrottle()

      val result = WorktreeCardService.renderCard(
        issueId,
        Map(issueId -> worktree),
        Map.empty,
        Map.empty,
        Map.empty,
        Map.empty, // Empty review state cache
        throttle,
        now,
        "test-server",
        (id: String) => Right(Issue(id, "Test Issue", "Open", None, None)),
        (id: String, tracker: String, config: Option[String]) => s"https://example.com/issue/$id"
      )

      // Verify fetchedReviewState is populated (the working pattern)
      assert(result.fetchedReviewState.isDefined, "fetchedReviewState should be Some when review-state.json exists")
      val cached = result.fetchedReviewState.get
      assertEquals(cached.state.status, Some("awaiting_review"))
      assertEquals(cached.state.phase, Some(1))
      assertEquals(cached.state.artifacts.size, 1)
      assertEquals(cached.state.artifacts.head.label, "Analysis")
    finally
      // Cleanup
      Files.deleteIfExists(reviewStateFile)
      Files.deleteIfExists(issueDir)
      Files.deleteIfExists(issueDir.getParent)
      Files.deleteIfExists(issueDir.getParent.getParent)
      Files.deleteIfExists(tempDir)

  test("renderCard returns None for fetchedReviewState when no review-state.json"):
    // Create temporary directory WITHOUT review-state.json
    val tempDir = Files.createTempDirectory("test-worktree-no-review")
    val issueDir = tempDir.resolve("project-management/issues/IW-NOREV")
    Files.createDirectories(issueDir)

    try
      val now = Instant.now()
      val issueId = "IW-NOREV"
      val worktree = WorktreeRegistration(
        issueId = issueId,
        path = tempDir.toString,
        trackerType = "Linear",
        team = "IW",
        registeredAt = now,
        lastSeenAt = now
      )
      val throttle = RefreshThrottle()

      val result = WorktreeCardService.renderCard(
        issueId,
        Map(issueId -> worktree),
        Map.empty,
        Map.empty,
        Map.empty,
        Map.empty, // Empty review state cache
        throttle,
        now,
        "test-server",
        (id: String) => Right(Issue(id, "Test Issue", "Open", None, None)),
        (id: String, tracker: String, config: Option[String]) => s"https://example.com/issue/$id"
      )

      // Verify fetchedReviewState is None when no file exists (normal case)
      assert(result.fetchedReviewState.isEmpty, "fetchedReviewState should be None when review-state.json doesn't exist")
    finally
      // Cleanup
      Files.deleteIfExists(issueDir)
      Files.deleteIfExists(issueDir.getParent)
      Files.deleteIfExists(issueDir.getParent.getParent)
      Files.deleteIfExists(tempDir)

  test("renderCard uses cached review state when mtime unchanged"):
    // Create temporary directory with review-state.json
    val tempDir = Files.createTempDirectory("test-worktree-cached")
    val issueDir = tempDir.resolve("project-management/issues/IW-CACHED")
    Files.createDirectories(issueDir)
    val reviewStateFile = issueDir.resolve("review-state.json")
    val reviewStateJson = """{
      "artifacts": [{"label": "New", "path": "new.md"}]
    }"""
    Files.write(reviewStateFile, reviewStateJson.getBytes)

    try
      val now = Instant.now()
      val issueId = "IW-CACHED"
      val worktree = WorktreeRegistration(
        issueId = issueId,
        path = tempDir.toString,
        trackerType = "Linear",
        team = "IW",
        registeredAt = now,
        lastSeenAt = now
      )
      val throttle = RefreshThrottle()

      // Pre-populate cache with existing review state (same mtime as file)
      val fileMtime = Files.getLastModifiedTime(reviewStateFile).toMillis
      val cachedState = ReviewState(
        status = Some("cached_status"),
        phase = Some(99),
        message = Some("Cached message"),
        artifacts = List(ReviewArtifact("Cached", "cached.md"))
      )
      val reviewStateCache = Map(
        issueId -> CachedReviewState(cachedState, Map(reviewStateFile.toString -> fileMtime))
      )

      val result = WorktreeCardService.renderCard(
        issueId,
        Map(issueId -> worktree),
        Map.empty,
        Map.empty,
        Map.empty,
        reviewStateCache, // Pre-populated cache
        throttle,
        now,
        "test-server",
        (id: String) => Right(Issue(id, "Test Issue", "Open", None, None)),
        (id: String, tracker: String, config: Option[String]) => s"https://example.com/issue/$id"
      )

      // Verify cached state is returned (cache hit)
      assert(result.fetchedReviewState.isDefined, "fetchedReviewState should be Some")
      val returned = result.fetchedReviewState.get
      assertEquals(returned.state.status, Some("cached_status"), "Should return cached state when mtime unchanged")
      assertEquals(returned.state.phase, Some(99))
    finally
      // Cleanup
      Files.deleteIfExists(reviewStateFile)
      Files.deleteIfExists(issueDir)
      Files.deleteIfExists(issueDir.getParent)
      Files.deleteIfExists(issueDir.getParent.getParent)
      Files.deleteIfExists(tempDir)

  // ============================================================================
  // Progress Persistence Tests (IW-164 Phase 2)
  // ============================================================================
  // These tests verify that progress data is fetched from filesystem on card
  // refresh and returned in CardRenderResult.fetchedProgress so the server
  // can update its cache.
  // ============================================================================

  test("renderCard returns fetchedProgress when phase task files exist"):
    // Create temporary directory with phase task files
    val tempDir = Files.createTempDirectory("test-worktree-progress")
    val issueDir = tempDir.resolve("project-management/issues/IW-PROG")
    Files.createDirectories(issueDir)

    // Create a phase task file with some completed tasks
    val phaseFile = issueDir.resolve("phase-01-tasks.md")
    val phaseContent =
      """# Phase 1 Tasks
        |
        |- [x] Task 1
        |- [x] Task 2
        |- [ ] Task 3
        |""".stripMargin
    Files.write(phaseFile, phaseContent.getBytes)

    try
      val now = Instant.now()
      val issueId = "IW-PROG"
      val worktree = WorktreeRegistration(
        issueId = issueId,
        path = tempDir.toString,
        trackerType = "Linear",
        team = "IW",
        registeredAt = now,
        lastSeenAt = now
      )
      val throttle = RefreshThrottle()

      val result = WorktreeCardService.renderCard(
        issueId,
        Map(issueId -> worktree),
        Map.empty,
        Map.empty, // Empty progress cache
        Map.empty,
        Map.empty,
        throttle,
        now,
        "test-server",
        (id: String) => Right(Issue(id, "Test Issue", "Open", None, None)),
        (id: String, tracker: String, config: Option[String]) => s"https://example.com/issue/$id"
      )

      // Verify fetchedProgress is populated
      assert(result.fetchedProgress.isDefined, "fetchedProgress should be Some when phase files exist")
      val cached = result.fetchedProgress.get
      assertEquals(cached.progress.totalPhases, 1)
      assertEquals(cached.progress.overallTotal, 3)
      assertEquals(cached.progress.overallCompleted, 2)
    finally
      // Cleanup
      Files.deleteIfExists(phaseFile)
      Files.deleteIfExists(issueDir)
      Files.deleteIfExists(issueDir.getParent)
      Files.deleteIfExists(issueDir.getParent.getParent)
      Files.deleteIfExists(tempDir)

  test("renderCard returns None for fetchedProgress when no phase files"):
    // Create temporary directory WITHOUT phase files
    val tempDir = Files.createTempDirectory("test-worktree-no-progress")
    val issueDir = tempDir.resolve("project-management/issues/IW-NOPROG")
    Files.createDirectories(issueDir)

    try
      val now = Instant.now()
      val issueId = "IW-NOPROG"
      val worktree = WorktreeRegistration(
        issueId = issueId,
        path = tempDir.toString,
        trackerType = "Linear",
        team = "IW",
        registeredAt = now,
        lastSeenAt = now
      )
      val throttle = RefreshThrottle()

      val result = WorktreeCardService.renderCard(
        issueId,
        Map(issueId -> worktree),
        Map.empty,
        Map.empty, // Empty progress cache
        Map.empty,
        Map.empty,
        throttle,
        now,
        "test-server",
        (id: String) => Right(Issue(id, "Test Issue", "Open", None, None)),
        (id: String, tracker: String, config: Option[String]) => s"https://example.com/issue/$id"
      )

      // Verify fetchedProgress is None when no phase files
      assert(result.fetchedProgress.isEmpty, "fetchedProgress should be None when no phase files exist")
    finally
      // Cleanup
      Files.deleteIfExists(issueDir)
      Files.deleteIfExists(issueDir.getParent)
      Files.deleteIfExists(issueDir.getParent.getParent)
      Files.deleteIfExists(tempDir)

  test("renderCard uses cached progress when mtime unchanged"):
    import iw.core.model.{WorkflowProgress, PhaseInfo, CachedProgress}

    // Create temporary directory with phase task file
    val tempDir = Files.createTempDirectory("test-worktree-cached-progress")
    val issueDir = tempDir.resolve("project-management/issues/IW-CPROG")
    Files.createDirectories(issueDir)

    val phaseFile = issueDir.resolve("phase-01-tasks.md")
    val phaseContent =
      """# Phase 1 Tasks
        |
        |- [x] New task
        |""".stripMargin
    Files.write(phaseFile, phaseContent.getBytes)

    try
      val now = Instant.now()
      val issueId = "IW-CPROG"
      val worktree = WorktreeRegistration(
        issueId = issueId,
        path = tempDir.toString,
        trackerType = "Linear",
        team = "IW",
        registeredAt = now,
        lastSeenAt = now
      )
      val throttle = RefreshThrottle()

      // Pre-populate cache with existing progress (same mtime as file)
      val fileMtime = Files.getLastModifiedTime(phaseFile).toMillis
      val cachedProgress = WorkflowProgress(
        currentPhase = Some(99),
        totalPhases = 99,
        phases = List(PhaseInfo(99, "Cached Phase", "/cached", 99, 50)),
        overallCompleted = 50,
        overallTotal = 99
      )
      val progressCache = Map(
        issueId -> CachedProgress(cachedProgress, Map(phaseFile.toString -> fileMtime))
      )

      val result = WorktreeCardService.renderCard(
        issueId,
        Map(issueId -> worktree),
        Map.empty,
        progressCache, // Pre-populated cache
        Map.empty,
        Map.empty,
        throttle,
        now,
        "test-server",
        (id: String) => Right(Issue(id, "Test Issue", "Open", None, None)),
        (id: String, tracker: String, config: Option[String]) => s"https://example.com/issue/$id"
      )

      // Verify cached state is returned (cache hit)
      assert(result.fetchedProgress.isDefined, "fetchedProgress should be Some")
      val returned = result.fetchedProgress.get
      assertEquals(returned.progress.totalPhases, 99, "Should return cached progress when mtime unchanged")
      assertEquals(returned.progress.overallTotal, 99)
    finally
      // Cleanup
      Files.deleteIfExists(phaseFile)
      Files.deleteIfExists(issueDir)
      Files.deleteIfExists(issueDir.getParent)
      Files.deleteIfExists(issueDir.getParent.getParent)
      Files.deleteIfExists(tempDir)
