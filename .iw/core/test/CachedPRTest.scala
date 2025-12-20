// PURPOSE: Unit tests for CachedPR domain model with TTL validation
// PURPOSE: Tests cache validation logic and age calculation for PR cache

package iw.core.domain

import munit.FunSuite
import java.time.Instant

class CachedPRTest extends FunSuite:

  def createTestPRData(): PullRequestData =
    PullRequestData(
      url = "https://github.com/org/repo/pull/42",
      state = PRState.Open,
      number = 42,
      title = "Test PR"
    )

  test("isValid returns true when within TTL (1 minute ago, TTL 2)"):
    val now = Instant.now()
    val fetchedAt = now.minusSeconds(60) // 1 minute ago
    val pr = createTestPRData()
    val cached = CachedPR(pr, fetchedAt)

    val result = CachedPR.isValid(cached, now)

    assert(result, "Cache should be valid when age < TTL")

  test("isValid returns false when TTL expired (3 minutes ago, TTL 2)"):
    val now = Instant.now()
    val fetchedAt = now.minusSeconds(180) // 3 minutes ago
    val pr = createTestPRData()
    val cached = CachedPR(pr, fetchedAt)

    val result = CachedPR.isValid(cached, now)

    assert(!result, "Cache should be invalid when age >= TTL")

  test("isValid returns true at just under TTL boundary (119 seconds ago, TTL 2)"):
    val now = Instant.now()
    val fetchedAt = now.minusSeconds(119) // 1 minute 59 seconds ago (< 2 min)
    val pr = createTestPRData()
    val cached = CachedPR(pr, fetchedAt)

    val result = CachedPR.isValid(cached, now)

    assert(result, "Cache should be valid when age is just under TTL")

  test("isValid returns false when exactly at TTL boundary (120 seconds = 2 minutes)"):
    val now = Instant.now()
    val fetchedAt = now.minusSeconds(120) // Exactly 2 minutes ago
    val pr = createTestPRData()
    val cached = CachedPR(pr, fetchedAt)

    val result = CachedPR.isValid(cached, now)

    assert(!result, "Cache should be invalid when age >= TTL (boundary case)")

  test("age calculates duration correctly (90 seconds)"):
    val now = Instant.now()
    val fetchedAt = now.minusSeconds(90)
    val pr = createTestPRData()
    val cached = CachedPR(pr, fetchedAt)

    val age = CachedPR.age(cached, now)

    assertEquals(age.toSeconds, 90L)

  test("age works for very recent data (5 seconds ago)"):
    val now = Instant.now()
    val fetchedAt = now.minusSeconds(5)
    val pr = createTestPRData()
    val cached = CachedPR(pr, fetchedAt)

    val age = CachedPR.age(cached, now)

    assertEquals(age.toSeconds, 5L)
    assertEquals(age.toMinutes, 0L)

  test("TTL_MINUTES is 2"):
    assertEquals(CachedPR.TTL_MINUTES, 2)

  test("CachedPR stores pr data correctly"):
    val pr = createTestPRData()
    val fetchedAt = Instant.now()
    val cached = CachedPR(pr, fetchedAt)

    assertEquals(cached.pr.url, "https://github.com/org/repo/pull/42")
    assertEquals(cached.pr.number, 42)
    assertEquals(cached.fetchedAt, fetchedAt)
