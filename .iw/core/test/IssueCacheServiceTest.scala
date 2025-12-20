// PURPOSE: Unit tests for IssueCacheService pure business logic
// PURPOSE: Tests cache-aware fetching with TTL, stale fallback, and URL building

package iw.core.application

import munit.FunSuite
import iw.core.{Issue, IssueId}
import iw.core.domain.{IssueData, CachedIssue}
import java.time.Instant
import scala.collection.mutable.ArrayBuffer

class IssueCacheServiceTest extends FunSuite:

  def createTestIssueData(id: String, fetchedAt: Instant): IssueData =
    IssueData(
      id = id,
      title = s"Issue $id",
      status = "Open",
      assignee = Some("Jane Doe"),
      description = Some("Description"),
      url = s"https://linear.app/issue/$id",
      fetchedAt = fetchedAt
    )

  def createTestIssue(id: String): Issue =
    Issue(id, s"Issue $id", "Open", Some("Jane Doe"), Some("Description"))

  test("fetchWithCache uses valid cache (2 min ago, no API call)"):
    val now = Instant.now()
    val cachedData = createTestIssueData("IWLE-123", now.minusSeconds(120)) // 2 min ago
    val cache = Map("IWLE-123" -> CachedIssue(cachedData, ttlMinutes = 5))

    val fetchFnCalls = ArrayBuffer[String]()
    val fetchFn = (id: String) => {
      fetchFnCalls += id
      Right(createTestIssue(id))
    }
    val urlBuilder = (id: String) => s"https://linear.app/$id"

    val result = IssueCacheService.fetchWithCache("IWLE-123", cache, now, fetchFn, urlBuilder)

    assert(result.isRight, "Should succeed with cached data")
    assertEquals(result.map(_._1.id).getOrElse(""), "IWLE-123")
    assertEquals(result.map(_._2).getOrElse(false), true, "fromCache should be true")
    assert(fetchFnCalls.isEmpty, "fetchFn should NOT be called when cache is valid")

  test("verify fetchFn NOT called when cache is valid"):
    val now = Instant.now()
    val cachedData = createTestIssueData("TEST-1", now.minusSeconds(60))
    val cache = Map("TEST-1" -> CachedIssue(cachedData))

    var wasCalled = false
    val fetchFn = (id: String) => {
      wasCalled = true
      Right(createTestIssue(id))
    }
    val urlBuilder = (id: String) => s"https://example.com/$id"

    IssueCacheService.fetchWithCache("TEST-1", cache, now, fetchFn, urlBuilder)

    assert(!wasCalled, "fetchFn must not be called when using valid cache")

  test("fetchWithCache refreshes expired cache (6 min ago, API called)"):
    val now = Instant.now()
    val cachedData = createTestIssueData("IWLE-123", now.minusSeconds(360)) // 6 min ago
    val cache = Map("IWLE-123" -> CachedIssue(cachedData, ttlMinutes = 5))

    val freshIssue = Issue("IWLE-123", "Fresh title", "In Progress", Some("John"), None)
    val fetchFn = (_: String) => Right(freshIssue)
    val urlBuilder = (id: String) => s"https://linear.app/issue/$id"

    val result = IssueCacheService.fetchWithCache("IWLE-123", cache, now, fetchFn, urlBuilder)

    assert(result.isRight, "Should succeed with fresh data")
    val (issueData, fromCache) = result.getOrElse((null, true))
    assertEquals(fromCache, false, "fromCache should be false for fresh fetch")
    assertEquals(issueData.title, "Fresh title", "Should have fresh data")
    assertEquals(issueData.fetchedAt, now, "Timestamp should be current")

  test("fresh data returned when cache expired and API succeeds"):
    val now = Instant.now()
    val oldData = createTestIssueData("PROJ-1", now.minusSeconds(600)) // 10 min ago
    val cache = Map("PROJ-1" -> CachedIssue(oldData))

    val freshIssue = Issue("PROJ-1", "Updated Issue", "Done", None, Some("New description"))
    val fetchFn = (_: String) => Right(freshIssue)
    val urlBuilder = (id: String) => s"https://example.com/$id"

    val result = IssueCacheService.fetchWithCache("PROJ-1", cache, now, fetchFn, urlBuilder)

    assert(result.isRight)
    val (issueData, fromCache) = result.getOrElse((null, true))
    assert(!fromCache, "Should use fresh data, not cache")
    assertEquals(issueData.title, "Updated Issue")
    assertEquals(issueData.status, "Done")
    assertEquals(issueData.description, Some("New description"))

  test("stale cache returned when API fails but cache exists"):
    val now = Instant.now()
    val cachedData = createTestIssueData("IWLE-456", now.minusSeconds(360)) // 6 min ago (stale)
    val cache = Map("IWLE-456" -> CachedIssue(cachedData, ttlMinutes = 5))

    val fetchFn = (_: String) => Left("API error: rate limit exceeded")
    val urlBuilder = (id: String) => s"https://linear.app/$id"

    val result = IssueCacheService.fetchWithCache("IWLE-456", cache, now, fetchFn, urlBuilder)

    assert(result.isRight, "Should still return data (stale cache fallback)")
    val (issueData, fromCache) = result.getOrElse((null, false))
    assertEquals(fromCache, true, "fromCache should be true when using stale fallback")
    assertEquals(issueData.id, "IWLE-456", "Should return stale cached data")

  test("fromCache=true when stale fallback used"):
    val now = Instant.now()
    val staleData = createTestIssueData("TEST-2", now.minusSeconds(1000))
    val cache = Map("TEST-2" -> CachedIssue(staleData))

    val fetchFn = (_: String) => Left("Network error")
    val urlBuilder = (id: String) => s"https://example.com/$id"

    val result = IssueCacheService.fetchWithCache("TEST-2", cache, now, fetchFn, urlBuilder)

    assertEquals(result.map(_._2).getOrElse(false), true, "Must indicate cache was used")

  test("error returned when no cache and API fails"):
    val now = Instant.now()
    val cache = Map.empty[String, CachedIssue]

    val fetchFn = (_: String) => Left("API error: unauthorized")
    val urlBuilder = (id: String) => s"https://linear.app/$id"

    val result = IssueCacheService.fetchWithCache("IWLE-789", cache, now, fetchFn, urlBuilder)

    assert(result.isLeft, "Should return error when no cache and API fails")
    assert(result.left.exists(_.contains("API error")))

  test("buildIssueUrl generates Linear URL correctly"):
    val url = IssueCacheService.buildIssueUrl("IWLE-123", "Linear", None)

    assert(url.contains("linear.app"), "Should contain linear.app domain")
    assert(url.contains("IWLE-123"), "Should contain issue ID")
    // Linear URL format: https://linear.app/team/{team}/issue/{issueId}
    // or simpler: https://linear.app/issue/{issueId}

  test("buildIssueUrl generates YouTrack URL correctly with baseUrl"):
    val baseUrl = "https://youtrack.example.com"
    val url = IssueCacheService.buildIssueUrl("PROJ-456", "YouTrack", Some(baseUrl))

    assert(url.contains("youtrack.example.com"), "Should contain YouTrack base URL")
    assert(url.contains("PROJ-456"), "Should contain issue ID")
    assertEquals(url, "https://youtrack.example.com/issue/PROJ-456")

  test("buildIssueUrl handles Linear issue with team extraction"):
    val url = IssueCacheService.buildIssueUrl("TEAM-123", "Linear", None)

    // Extract team (TEAM) from TEAM-123
    assert(url.contains("TEAM") || url.contains("linear.app"))

  test("fetchWithCache handles missing issue ID in cache"):
    val now = Instant.now()
    val cache = Map("IWLE-1" -> CachedIssue(createTestIssueData("IWLE-1", now)))

    val freshIssue = Issue("IWLE-2", "New Issue", "Open", None, None)
    val fetchFn = (_: String) => Right(freshIssue)
    val urlBuilder = (id: String) => s"https://linear.app/$id"

    val result = IssueCacheService.fetchWithCache("IWLE-2", cache, now, fetchFn, urlBuilder)

    assert(result.isRight)
    val (issueData, fromCache) = result.getOrElse((null, true))
    assert(!fromCache, "Should fetch fresh data when not in cache")
    assertEquals(issueData.id, "IWLE-2")
