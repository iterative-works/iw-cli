// PURPOSE: Unit tests for CachedIssue domain model with TTL validation
// PURPOSE: Tests cache validation logic and age calculation

package iw.core.domain

import munit.FunSuite
import iw.core.Issue
import java.time.Instant

class CachedIssueTest extends FunSuite:

  def createTestIssueData(fetchedAt: Instant): IssueData =
    IssueData(
      id = "IWLE-123",
      title = "Test Issue",
      status = "Open",
      assignee = Some("Jane Doe"),
      description = Some("Description"),
      url = "https://linear.app/issue/IWLE-123",
      fetchedAt = fetchedAt
    )

  test("isValid returns true for fresh cache (2 minutes ago, TTL 5)"):
    val now = Instant.now()
    val fetchedAt = now.minusSeconds(120) // 2 minutes ago
    val issueData = createTestIssueData(fetchedAt)
    val cached = CachedIssue(issueData, ttlMinutes = 5)

    val result = CachedIssue.isValid(cached, now)

    assert(result, "Cache should be valid when age < TTL")

  test("isValid returns false for expired cache (6 minutes ago, TTL 5)"):
    val now = Instant.now()
    val fetchedAt = now.minusSeconds(360) // 6 minutes ago
    val issueData = createTestIssueData(fetchedAt)
    val cached = CachedIssue(issueData, ttlMinutes = 5)

    val result = CachedIssue.isValid(cached, now)

    assert(!result, "Cache should be invalid when age >= TTL")

  test("isValid returns true at exactly TTL boundary (5 minutes ago, TTL 5)"):
    val now = Instant.now()
    val fetchedAt = now.minusSeconds(299) // 4 minutes 59 seconds ago (< 5 min)
    val issueData = createTestIssueData(fetchedAt)
    val cached = CachedIssue(issueData, ttlMinutes = 5)

    val result = CachedIssue.isValid(cached, now)

    assert(result, "Cache should be valid when age is just under TTL")

  test("isValid returns false when exactly at TTL boundary (5 minutes)"):
    val now = Instant.now()
    val fetchedAt = now.minusSeconds(300) // Exactly 5 minutes ago
    val issueData = createTestIssueData(fetchedAt)
    val cached = CachedIssue(issueData, ttlMinutes = 5)

    val result = CachedIssue.isValid(cached, now)

    assert(!result, "Cache should be invalid when age >= TTL (boundary case)")

  test("age calculates duration correctly (3 minutes = 180 seconds)"):
    val now = Instant.now()
    val fetchedAt = now.minusSeconds(180) // 3 minutes ago
    val issueData = createTestIssueData(fetchedAt)
    val cached = CachedIssue(issueData)

    val age = CachedIssue.age(cached, now)

    assertEquals(age.toMinutes, 3L)
    assertEquals(age.toSeconds, 180L)

  test("default TTL is 5 minutes"):
    val issueData = createTestIssueData(Instant.now())
    val cached = CachedIssue(issueData)

    assertEquals(cached.ttlMinutes, 5)

  test("age works for very recent data (10 seconds ago)"):
    val now = Instant.now()
    val fetchedAt = now.minusSeconds(10)
    val issueData = createTestIssueData(fetchedAt)
    val cached = CachedIssue(issueData)

    val age = CachedIssue.age(cached, now)

    assertEquals(age.toSeconds, 10L)
    assertEquals(age.toMinutes, 0L)

  test("age works for old data (30 days ago)"):
    val now = Instant.now()
    val fetchedAt = now.minusSeconds(30 * 24 * 60 * 60) // 30 days
    val issueData = createTestIssueData(fetchedAt)
    val cached = CachedIssue(issueData)

    val age = CachedIssue.age(cached, now)

    assertEquals(age.toDays, 30L)

  test("custom TTL can be set"):
    val issueData = createTestIssueData(Instant.now())
    val cached = CachedIssue(issueData, ttlMinutes = 10)

    assertEquals(cached.ttlMinutes, 10)

  test("isStale returns true when cache older than TTL"):
    val now = Instant.now()
    val fetchedAt = now.minusSeconds(360) // 6 minutes ago
    val issueData = createTestIssueData(fetchedAt)
    val cached = CachedIssue(issueData, ttlMinutes = 5)

    val result = CachedIssue.isStale(cached, now)

    assert(result, "Cache should be stale when age >= TTL")

  test("isStale returns false when cache newer than TTL"):
    val now = Instant.now()
    val fetchedAt = now.minusSeconds(120) // 2 minutes ago
    val issueData = createTestIssueData(fetchedAt)
    val cached = CachedIssue(issueData, ttlMinutes = 5)

    val result = CachedIssue.isStale(cached, now)

    assert(!result, "Cache should not be stale when age < TTL")
