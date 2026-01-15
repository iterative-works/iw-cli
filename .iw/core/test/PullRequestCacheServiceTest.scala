// PURPOSE: Unit tests for PullRequestCacheService application logic
// PURPOSE: Tests PR fetching with cache validation and JSON parsing

package iw.core.application

import munit.FunSuite
import iw.core.domain.{PullRequestData, PRState, CachedPR}
import java.time.Instant

class PullRequestCacheServiceTest extends FunSuite:

  val mockPRData = PullRequestData(
    url = "https://github.com/org/repo/pull/42",
    state = PRState.Open,
    number = 42,
    title = "Test PR"
  )

  test("fetchPR uses cache when valid (<2 min)"):
    val now = Instant.now()
    val cachedPR = CachedPR(mockPRData, now.minusSeconds(60)) // 1 minute ago
    val cache = Map("IWLE-123" -> cachedPR)

    var execCommandCalled = false
    val execCommand = (cmd: String, args: Array[String]) => {
      execCommandCalled = true
      Right("{}")
    }
    val detectTool = (tool: String) => true

    val result = PullRequestCacheService.fetchPR(
      "/path", cache, "IWLE-123", now, execCommand, detectTool
    )

    assert(result.isRight)
    assert(!execCommandCalled, "Should use cache, not execute command")
    assertEquals(result.toOption.flatten, Some(mockPRData))

  test("fetchPR re-fetches when cache expired (>2 min)"):
    val now = Instant.now()
    val cachedPR = CachedPR(mockPRData, now.minusSeconds(180)) // 3 minutes ago (expired)
    val cache = Map("IWLE-123" -> cachedPR)

    val execCommand = (cmd: String, args: Array[String]) =>
      Right("""{"url": "https://github.com/org/repo/pull/99", "state": "OPEN", "number": 99, "title": "New"}""")
    val detectTool = (tool: String) => tool == "gh"

    val result = PullRequestCacheService.fetchPR(
      "/path", cache, "IWLE-123", now, execCommand, detectTool
    )

    assert(result.isRight)
    val prData = result.toOption.flatten
    assert(prData.isDefined)
    assertEquals(prData.get.number, 99) // Fresh data, not cached

  test("fetchPR returns None when no PR tool available"):
    val cache = Map.empty[String, CachedPR]
    val execCommand = (cmd: String, args: Array[String]) => Right("")
    val detectTool = (tool: String) => false // No tools available

    val result = PullRequestCacheService.fetchPR(
      "/path", cache, "IWLE-123", Instant.now(), execCommand, detectTool
    )

    assert(result.isRight)
    assertEquals(result.toOption.flatten, None)

  test("fetchPR returns None when PR not found (404)"):
    val cache = Map.empty[String, CachedPR]
    val execCommand = (cmd: String, args: Array[String]) =>
      Left("no pull requests found") // gh pr view returns error when no PR
    val detectTool = (tool: String) => tool == "gh"

    val result = PullRequestCacheService.fetchPR(
      "/path", cache, "IWLE-123", Instant.now(), execCommand, detectTool
    )

    assert(result.isRight)
    assertEquals(result.toOption.flatten, None)

  test("parseGitHubPR extracts url, state, number, title"):
    val json = """{"url": "https://github.com/org/repo/pull/42", "state": "OPEN", "number": 42, "title": "Add feature"}"""
    val result = PullRequestCacheService.parseGitHubPR(json)

    assert(result.isRight)
    val pr = result.toOption.get
    assertEquals(pr.url, "https://github.com/org/repo/pull/42")
    assertEquals(pr.state, PRState.Open)
    assertEquals(pr.number, 42)
    assertEquals(pr.title, "Add feature")

  test("parseGitHubPR handles MERGED state"):
    val json = """{"url": "https://github.com/org/repo/pull/42", "state": "MERGED", "number": 42, "title": "Fix bug"}"""
    val result = PullRequestCacheService.parseGitHubPR(json)

    assert(result.isRight)
    assertEquals(result.map(_.state), Right(PRState.Merged))

  test("parseGitHubPR handles CLOSED state"):
    val json = """{"url": "https://github.com/org/repo/pull/42", "state": "CLOSED", "number": 42, "title": "Old PR"}"""
    val result = PullRequestCacheService.parseGitHubPR(json)

    assert(result.isRight)
    assertEquals(result.map(_.state), Right(PRState.Closed))

  test("parseGitHubPR returns Left for invalid JSON"):
    val result = PullRequestCacheService.parseGitHubPR("not valid json")

    assert(result.isLeft)

  test("parseGitLabPR extracts url, state, iid (number), title"):
    val json = """{"url": "https://gitlab.com/org/repo/-/merge_requests/42", "state": "opened", "iid": 42, "title": "Add feature"}"""
    val result = PullRequestCacheService.parseGitLabPR(json)

    assert(result.isRight)
    val pr = result.toOption.get
    assertEquals(pr.url, "https://gitlab.com/org/repo/-/merge_requests/42")
    assertEquals(pr.state, PRState.Open)
    assertEquals(pr.number, 42)
    assertEquals(pr.title, "Add feature")

  test("parseGitLabPR handles merged state"):
    val json = """{"url": "https://gitlab.com/org/repo/-/merge_requests/42", "state": "merged", "iid": 42, "title": "Fix"}"""
    val result = PullRequestCacheService.parseGitLabPR(json)

    assert(result.isRight)
    assertEquals(result.map(_.state), Right(PRState.Merged))

  test("parseGitLabPR handles closed state"):
    val json = """{"url": "https://gitlab.com/org/repo/-/merge_requests/42", "state": "closed", "iid": 42, "title": "Old"}"""
    val result = PullRequestCacheService.parseGitLabPR(json)

    assert(result.isRight)
    assertEquals(result.map(_.state), Right(PRState.Closed))

  test("detectPRTool returns Some(gh) when gh available"):
    val detectTool = (tool: String) => tool == "gh"
    assertEquals(PullRequestCacheService.detectPRTool(detectTool), Some("gh"))

  test("detectPRTool returns Some(glab) when only glab available"):
    val detectTool = (tool: String) => tool == "glab"
    assertEquals(PullRequestCacheService.detectPRTool(detectTool), Some("glab"))

  test("detectPRTool prefers gh when both available"):
    val detectTool = (tool: String) => true // Both available
    assertEquals(PullRequestCacheService.detectPRTool(detectTool), Some("gh"))

  test("detectPRTool returns None when neither available"):
    val detectTool = (tool: String) => false
    assertEquals(PullRequestCacheService.detectPRTool(detectTool), None)

  // getCachedOnly tests
  test("getCachedOnly returns cached PR when cache exists"):
    val now = Instant.now()
    val cachedPR = CachedPR(mockPRData, now.minusSeconds(60))
    val cache = Map("IWLE-123" -> cachedPR)

    val result = PullRequestCacheService.getCachedOnly("IWLE-123", cache)

    assert(result.isDefined, "Should return cached PR data")
    assertEquals(result.map(_.number).getOrElse(0), 42)

  test("getCachedOnly returns None when cache is empty"):
    val cache = Map.empty[String, CachedPR]

    val result = PullRequestCacheService.getCachedOnly("IWLE-456", cache)

    assert(result.isEmpty, "Should return None when cache is empty")

  test("getCachedOnly does NOT call CLI"):
    val now = Instant.now()
    val cachedPR = CachedPR(mockPRData, now.minusSeconds(60))
    val cache = Map("TEST-1" -> cachedPR)

    // This test is structural - getCachedOnly doesn't take exec/detect functions
    // The test verifies that the method signature doesn't require them
    val result = PullRequestCacheService.getCachedOnly("TEST-1", cache)

    assert(result.isDefined, "Should return cached data without CLI calls")

  test("getCachedOnly returns stale cache without calling CLI"):
    val now = Instant.now()
    val stalePR = CachedPR(mockPRData, now.minusSeconds(600)) // 10 minutes ago (very stale)
    val cache = Map("IWLE-789" -> stalePR)

    val result = PullRequestCacheService.getCachedOnly("IWLE-789", cache)

    assert(result.isDefined, "Should return even stale cached data")
    assertEquals(result.map(_.number).getOrElse(0), 42)

  test("fetchPR preserves stale cache when CLI command fails"):
    val now = Instant.now()
    val stalePR = CachedPR(mockPRData, now.minusSeconds(180)) // 3 minutes ago (expired)
    val cache = Map("IWLE-123" -> stalePR)

    val execCommand = (cmd: String, args: Array[String]) =>
      Left("API error: rate limit exceeded")
    val detectTool = (tool: String) => tool == "gh"

    val result = PullRequestCacheService.fetchPR(
      "/path", cache, "IWLE-123", now, execCommand, detectTool
    )

    assert(result.isRight, "Should return stale cache data when API fails")
    val prData = result.toOption.flatten
    assert(prData.isDefined, "Should have PR data from stale cache")
    assertEquals(prData.get.number, 42, "Should return original cached data, not try to fetch")
