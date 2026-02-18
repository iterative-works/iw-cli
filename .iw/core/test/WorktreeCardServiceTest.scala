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
      display = None,
      badges = None,
      taskLists = None,
      needsAttention = None,
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

  // ============================================================================
  // PR Persistence Tests (IW-164 Phase 3)
  // ============================================================================
  // These tests verify that PR data from the cache is returned in
  // CardRenderResult.fetchedPR so the server can update its cache after HTMX
  // card refresh. Unlike progress (mtime-based), PR uses TTL-based caching.
  // ============================================================================

  test("renderCard returns fetchedPR when PR cache has data"):
    import iw.core.model.{PullRequestData, PRState, CachedPR}

    val now = Instant.now()
    val issueId = "IW-PR"
    val worktree = WorktreeRegistration(
      issueId = issueId,
      path = "/tmp/worktree-pr",
      trackerType = "Linear",
      team = "IW",
      registeredAt = now,
      lastSeenAt = now
    )
    val throttle = RefreshThrottle()

    // Pre-populate PR cache
    val prData = PullRequestData(
      url = "https://github.com/org/repo/pull/123",
      state = PRState.Open,
      number = 123,
      title = "Test PR"
    )
    val cachedPR = CachedPR(prData, now)
    val prCache = Map(issueId -> cachedPR)

    val result = WorktreeCardService.renderCard(
      issueId,
      Map(issueId -> worktree),
      Map.empty,
      Map.empty,
      prCache, // Pre-populated PR cache
      Map.empty,
      throttle,
      now,
      "test-server",
      (id: String) => Right(Issue(id, "Test Issue", "Open", None, None)),
      (id: String, tracker: String, config: Option[String]) => s"https://example.com/issue/$id"
    )

    // Verify fetchedPR is populated when cache has data
    assert(result.fetchedPR.isDefined, "fetchedPR should be Some when PR cache has data")
    val returned = result.fetchedPR.get
    assertEquals(returned.pr.number, 123)
    assertEquals(returned.pr.title, "Test PR")
    assertEquals(returned.pr.state, PRState.Open)

  test("renderCard returns None for fetchedPR when no PR cached"):
    val now = Instant.now()
    val issueId = "IW-NOPR"
    val worktree = WorktreeRegistration(
      issueId = issueId,
      path = "/tmp/worktree-no-pr",
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
      Map.empty, // Empty PR cache
      Map.empty,
      throttle,
      now,
      "test-server",
      (id: String) => Right(Issue(id, "Test Issue", "Open", None, None)),
      (id: String, tracker: String, config: Option[String]) => s"https://example.com/issue/$id"
    )

    // Verify fetchedPR is None when no cache data and no fetchPR function
    assert(result.fetchedPR.isEmpty, "fetchedPR should be None when PR cache is empty and no fetchPR provided")

  // ============================================================================
  // PR Fresh Fetch Tests (IW-203)
  // ============================================================================
  // These tests verify that PR data is fetched fresh via CLI when the card
  // refresh is not throttled, so PR links actually appear on worktree cards.
  // ============================================================================

  test("renderCard fetches fresh PR when not throttled and fetchPR provided"):
    import iw.core.model.{PullRequestData, PRState, CachedPR}

    val now = Instant.now()
    val issueId = "IW-FRESHPR"
    val worktree = WorktreeRegistration(
      issueId = issueId,
      path = "/tmp/worktree-fresh-pr",
      trackerType = "GitHub",
      team = "IW",
      registeredAt = now,
      lastSeenAt = now
    )
    val throttle = RefreshThrottle()

    // Provide a fetchPR function that returns fresh PR data
    val freshPR = PullRequestData(
      url = "https://github.com/org/repo/pull/42",
      state = PRState.Open,
      number = 42,
      title = "Fresh PR"
    )
    val fetchPR = () => Right(Some(freshPR)): Either[String, Option[PullRequestData]]

    val result = WorktreeCardService.renderCard(
      issueId,
      Map(issueId -> worktree),
      Map.empty,
      Map.empty,
      Map.empty, // Empty PR cache - no existing data
      Map.empty,
      throttle,
      now,
      "test-server",
      (id: String) => Right(Issue(id, "Test Issue", "Open", None, None)),
      (id: String, tracker: String, config: Option[String]) => s"https://example.com/issue/$id",
      fetchPR
    )

    // Verify fresh PR data is fetched and returned for caching
    assert(result.fetchedPR.isDefined, "fetchedPR should be Some when fetchPR returns data")
    val returned = result.fetchedPR.get
    assertEquals(returned.pr.number, 42)
    assertEquals(returned.pr.title, "Fresh PR")
    assertEquals(returned.pr.state, PRState.Open)

    // Verify PR data appears in rendered HTML
    assert(result.html.contains("pr-link"), "HTML should contain PR link section")
    assert(result.html.contains("https://github.com/org/repo/pull/42"), "HTML should contain PR URL")

  test("renderCard falls back to cached PR when fetchPR fails"):
    import iw.core.model.{PullRequestData, PRState, CachedPR}

    val now = Instant.now()
    val issueId = "IW-FAILPR"
    val worktree = WorktreeRegistration(
      issueId = issueId,
      path = "/tmp/worktree-fail-pr",
      trackerType = "GitHub",
      team = "IW",
      registeredAt = now,
      lastSeenAt = now
    )
    val throttle = RefreshThrottle()

    // Pre-populate PR cache with existing data
    val cachedPrData = PullRequestData(
      url = "https://github.com/org/repo/pull/99",
      state = PRState.Open,
      number = 99,
      title = "Cached PR"
    )
    val prCache = Map(issueId -> CachedPR(cachedPrData, now))

    // Provide a fetchPR that fails
    val fetchPR = () => Left("gh command failed"): Either[String, Option[PullRequestData]]

    val result = WorktreeCardService.renderCard(
      issueId,
      Map(issueId -> worktree),
      Map.empty,
      Map.empty,
      prCache,
      Map.empty,
      throttle,
      now,
      "test-server",
      (id: String) => Right(Issue(id, "Test Issue", "Open", None, None)),
      (id: String, tracker: String, config: Option[String]) => s"https://example.com/issue/$id",
      fetchPR
    )

    // Should fall back to cached PR data
    assert(result.fetchedPR.isDefined, "fetchedPR should fall back to cached data on fetch failure")
    assertEquals(result.fetchedPR.get.pr.number, 99)

  test("renderCard does not call fetchPR when throttled"):
    import iw.core.model.{PullRequestData, PRState, CachedPR}

    val now = Instant.now()
    val issueId = "IW-THROTTPR"
    val worktree = WorktreeRegistration(
      issueId = issueId,
      path = "/tmp/worktree-throttle-pr",
      trackerType = "GitHub",
      team = "IW",
      registeredAt = now,
      lastSeenAt = now
    )
    val throttle = RefreshThrottle()
    // Record a recent refresh to trigger throttling
    throttle.recordRefresh(issueId, now)

    var fetchPRCalled = false
    val fetchPR = () => {
      fetchPRCalled = true
      Right(Some(PullRequestData("url", PRState.Open, 1, "Should not be called"))): Either[String, Option[PullRequestData]]
    }

    // Pre-populate caches so we get a rendered card (not skeleton)
    val issueData = IssueData(
      id = issueId, title = "Test", status = "Open",
      assignee = None, description = None,
      url = "https://example.com", fetchedAt = now
    )

    val result = WorktreeCardService.renderCard(
      issueId,
      Map(issueId -> worktree),
      Map(issueId -> CachedIssue(issueData)),
      Map.empty,
      Map.empty,
      Map.empty,
      throttle,
      now,
      "test-server",
      (id: String) => Right(Issue(id, "Test Issue", "Open", None, None)),
      (id: String, tracker: String, config: Option[String]) => s"https://example.com/issue/$id",
      fetchPR
    )

    // fetchPR should NOT have been called because refresh is throttled
    assert(!fetchPRCalled, "fetchPR should not be called when throttled")
