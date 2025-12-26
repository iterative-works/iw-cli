// PURPOSE: Unit and integration tests for ReviewStateService
// PURPOSE: Verify JSON parsing and file I/O with caching

package iw.core.test

import iw.core.application.ReviewStateService
import iw.core.domain.{ReviewState, ReviewArtifact, CachedReviewState}

class ReviewStateServiceTest extends munit.FunSuite:

  // JSON Parsing Tests

  test("parseReviewStateJson parses valid JSON with all fields"):
    val json = """{
      "status": "awaiting_review",
      "phase": 8,
      "message": "Ready for review",
      "artifacts": [
        {"label": "Analysis", "path": "project-management/issues/46/analysis.md"}
      ]
    }"""

    val result = ReviewStateService.parseReviewStateJson(json)
    assert(result.isRight)

    val state = result.toOption.get
    assertEquals(state.status, Some("awaiting_review"))
    assertEquals(state.phase, Some(8))
    assertEquals(state.message, Some("Ready for review"))
    assertEquals(state.artifacts.size, 1)
    assertEquals(state.artifacts.head.label, "Analysis")
    assertEquals(state.artifacts.head.path, "project-management/issues/46/analysis.md")

  test("parseReviewStateJson parses minimal JSON (only artifacts)"):
    val json = """{
      "artifacts": [
        {"label": "Doc", "path": "path/to/doc.md"}
      ]
    }"""

    val result = ReviewStateService.parseReviewStateJson(json)
    assert(result.isRight)

    val state = result.toOption.get
    assertEquals(state.status, None)
    assertEquals(state.phase, None)
    assertEquals(state.message, None)
    assertEquals(state.artifacts.size, 1)
    assertEquals(state.artifacts.head.label, "Doc")

  test("parseReviewStateJson returns error for missing artifacts"):
    val json = """{
      "status": "awaiting_review"
    }"""

    val result = ReviewStateService.parseReviewStateJson(json)
    assert(result.isLeft)
    // Just verify it's an error - the specific message may vary
    assert(result.left.exists(_.nonEmpty))

  test("parseReviewStateJson returns error for invalid JSON syntax"):
    val json = """{ invalid json }"""

    val result = ReviewStateService.parseReviewStateJson(json)
    assert(result.isLeft)

  test("parseReviewStateJson handles optional fields as None"):
    val json = """{
      "artifacts": [
        {"label": "Test", "path": "test.md"}
      ]
    }"""

    val result = ReviewStateService.parseReviewStateJson(json)
    assert(result.isRight)

    val state = result.toOption.get
    assertEquals(state.status, None)
    assertEquals(state.phase, None)
    assertEquals(state.message, None)

  test("parseReviewStateJson handles multiple artifacts"):
    val json = """{
      "artifacts": [
        {"label": "Analysis", "path": "analysis.md"},
        {"label": "Context", "path": "context.md"},
        {"label": "Tasks", "path": "tasks.md"}
      ]
    }"""

    val result = ReviewStateService.parseReviewStateJson(json)
    assert(result.isRight)

    val state = result.toOption.get
    assertEquals(state.artifacts.size, 3)
    assertEquals(state.artifacts.map(_.label), List("Analysis", "Context", "Tasks"))

  test("parseReviewStateJson handles empty artifacts list"):
    val json = """{
      "artifacts": []
    }"""

    val result = ReviewStateService.parseReviewStateJson(json)
    assert(result.isRight)

    val state = result.toOption.get
    assertEquals(state.artifacts.size, 0)

  // fetchReviewState Tests

  test("fetchReviewState returns cached state when mtime unchanged"):
    val issueId = "IWLE-123"
    val worktreePath = "/path/to/worktree"
    val reviewStatePath = s"$worktreePath/project-management/issues/$issueId/review-state.json"

    val cachedState = ReviewState(
      status = Some("cached"),
      phase = Some(1),
      message = None,
      artifacts = List(ReviewArtifact("Cached", "cached.md"))
    )
    val cache = Map(
      issueId -> CachedReviewState(cachedState, Map(reviewStatePath -> 1000L))
    )

    // Mock I/O: return same mtime
    val getMtime = (path: String) => Right(1000L)
    val readFile = (path: String) => fail("File should not be read when cache is valid")

    val result = ReviewStateService.fetchReviewState(issueId, worktreePath, cache, readFile, getMtime)
    assert(result.isRight)
    assertEquals(result.toOption.get.state, cachedState)

  test("fetchReviewState re-reads file when mtime changed"):
    val issueId = "IWLE-123"
    val worktreePath = "/path/to/worktree"
    val reviewStatePath = s"$worktreePath/project-management/issues/$issueId/review-state.json"

    val oldState = ReviewState(None, None, None, List(ReviewArtifact("Old", "old.md")))
    val cache = Map(
      issueId -> CachedReviewState(oldState, Map(reviewStatePath -> 1000L))
    )

    val newJson = """{
      "artifacts": [{"label": "New", "path": "new.md"}]
    }"""

    // Mock I/O: mtime changed, file has new content
    val getMtime = (path: String) => Right(2000L) // Changed mtime
    val readFile = (path: String) => Right(newJson)

    val result = ReviewStateService.fetchReviewState(issueId, worktreePath, cache, readFile, getMtime)
    assert(result.isRight)

    val cached = result.toOption.get
    assertEquals(cached.state.artifacts.head.label, "New")

  test("fetchReviewState returns error for missing file"):
    val issueId = "IWLE-123"
    val worktreePath = "/path/to/worktree"
    val cache = Map.empty[String, CachedReviewState]

    // Mock I/O: file doesn't exist
    val getMtime = (path: String) => Left("File not found")
    val readFile = (path: String) => Left("File not found")

    val result = ReviewStateService.fetchReviewState(issueId, worktreePath, cache, readFile, getMtime)
    assert(result.isLeft)

  test("fetchReviewState returns error for invalid JSON"):
    val issueId = "IWLE-123"
    val worktreePath = "/path/to/worktree"
    val cache = Map.empty[String, CachedReviewState]

    val invalidJson = """{ invalid }"""

    // Mock I/O: file exists with invalid content
    val getMtime = (path: String) => Right(1000L)
    val readFile = (path: String) => Right(invalidJson)

    val result = ReviewStateService.fetchReviewState(issueId, worktreePath, cache, readFile, getMtime)
    assert(result.isLeft)

  test("fetchReviewState handles cache miss (reads and parses file)"):
    val issueId = "IWLE-123"
    val worktreePath = "/path/to/worktree"
    val cache = Map.empty[String, CachedReviewState] // Empty cache

    val validJson = """{
      "artifacts": [{"label": "Test", "path": "test.md"}]
    }"""

    // Mock I/O
    val getMtime = (path: String) => Right(1000L)
    val readFile = (path: String) => Right(validJson)

    val result = ReviewStateService.fetchReviewState(issueId, worktreePath, cache, readFile, getMtime)
    assert(result.isRight)

    val cached = result.toOption.get
    assertEquals(cached.state.artifacts.size, 1)
    assertEquals(cached.state.artifacts.head.label, "Test")

  // Phase 5: Cache returns CachedReviewState tests

  test("fetchReviewState cache hit returns CachedReviewState without file read"):
    val issueId = "IWLE-123"
    val worktreePath = "/path/to/worktree"
    val reviewStatePath = s"$worktreePath/project-management/issues/$issueId/review-state.json"

    val cachedState = ReviewState(
      status = Some("awaiting_review"),
      phase = Some(8),
      message = Some("Ready"),
      artifacts = List(ReviewArtifact("Analysis", "analysis.md"))
    )
    val cachedReviewState = CachedReviewState(cachedState, Map(reviewStatePath -> 1000L))
    val cache = Map(issueId -> cachedReviewState)

    // Mock I/O: getMtime returns same value, readFile should NOT be called
    val getMtime = (path: String) => Right(1000L)
    var fileReadCalled = false
    val readFile = (path: String) => {
      fileReadCalled = true
      fail("File should not be read when cache is valid")
    }

    val result = ReviewStateService.fetchReviewState(issueId, worktreePath, cache, readFile, getMtime)

    // Should return CachedReviewState
    assert(result.isRight)
    val returned = result.toOption.get
    assert(returned.isInstanceOf[CachedReviewState])
    assertEquals(returned, cachedReviewState)
    assert(!fileReadCalled)

  test("fetchReviewState cache miss returns new CachedReviewState with updated mtime"):
    val issueId = "IWLE-123"
    val worktreePath = "/path/to/worktree"
    val reviewStatePath = s"$worktreePath/project-management/issues/$issueId/review-state.json"

    val oldState = ReviewState(None, None, None, List(ReviewArtifact("Old", "old.md")))
    val oldCached = CachedReviewState(oldState, Map(reviewStatePath -> 1000L))
    val cache = Map(issueId -> oldCached)

    val newJson = """{
      "status": "in_progress",
      "phase": 2,
      "artifacts": [{"label": "New", "path": "new.md"}]
    }"""

    // Mock I/O: mtime changed, file has new content
    val getMtime = (path: String) => Right(2000L) // Changed mtime
    var fileReadCalled = false
    val readFile = (path: String) => {
      fileReadCalled = true
      Right(newJson)
    }

    val result = ReviewStateService.fetchReviewState(issueId, worktreePath, cache, readFile, getMtime)

    // Should return new CachedReviewState
    assert(result.isRight)
    val returned = result.toOption.get
    assert(returned.isInstanceOf[CachedReviewState])

    // Verify state was re-parsed
    assertEquals(returned.state.status, Some("in_progress"))
    assertEquals(returned.state.phase, Some(2))
    assertEquals(returned.state.artifacts.head.label, "New")

    // Verify mtime was updated
    assertEquals(returned.filesMtime(reviewStatePath), 2000L)

    // Verify file was read
    assert(fileReadCalled)

  test("fetchReviewState first fetch creates CachedReviewState"):
    val issueId = "IWLE-123"
    val worktreePath = "/path/to/worktree"
    val reviewStatePath = s"$worktreePath/project-management/issues/$issueId/review-state.json"
    val cache = Map.empty[String, CachedReviewState] // Empty cache

    val validJson = """{
      "status": "awaiting_review",
      "phase": 5,
      "message": "Please review",
      "artifacts": [
        {"label": "Analysis", "path": "analysis.md"},
        {"label": "Tasks", "path": "tasks.md"}
      ]
    }"""

    // Mock I/O
    val getMtime = (path: String) => Right(1500L)
    val readFile = (path: String) => Right(validJson)

    val result = ReviewStateService.fetchReviewState(issueId, worktreePath, cache, readFile, getMtime)

    // Should return CachedReviewState
    assert(result.isRight)
    val returned = result.toOption.get
    assert(returned.isInstanceOf[CachedReviewState])

    // Verify state was parsed correctly
    assertEquals(returned.state.status, Some("awaiting_review"))
    assertEquals(returned.state.phase, Some(5))
    assertEquals(returned.state.message, Some("Please review"))
    assertEquals(returned.state.artifacts.size, 2)

    // Verify mtime was captured
    assert(returned.filesMtime.contains(reviewStatePath))
    assertEquals(returned.filesMtime(reviewStatePath), 1500L)
