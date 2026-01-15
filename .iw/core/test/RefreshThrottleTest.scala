// PURPOSE: Unit tests for RefreshThrottle rate limiting logic
// PURPOSE: Validates per-worktree 30s throttle behavior

package iw.tests

import iw.core.application.RefreshThrottle
import java.time.Instant
import java.time.temporal.ChronoUnit

class RefreshThrottleTest extends munit.FunSuite:
  test("shouldRefresh returns true when worktree never refreshed") {
    val throttle = RefreshThrottle()
    val now = Instant.now()

    assert(throttle.shouldRefresh("IW-1", now))
  }

  test("shouldRefresh returns false when refreshed < 30s ago") {
    val throttle = RefreshThrottle()
    val now = Instant.now()

    // Record refresh
    throttle.recordRefresh("IW-1", now)

    // Try to refresh 10 seconds later
    val later = now.plus(10, ChronoUnit.SECONDS)
    assert(!throttle.shouldRefresh("IW-1", later))
  }

  test("shouldRefresh returns true when refreshed >= 30s ago") {
    val throttle = RefreshThrottle()
    val now = Instant.now()

    // Record refresh
    throttle.recordRefresh("IW-1", now)

    // Try to refresh 30 seconds later
    val later = now.plus(30, ChronoUnit.SECONDS)
    assert(throttle.shouldRefresh("IW-1", later))
  }

  test("shouldRefresh returns true when refreshed > 30s ago") {
    val throttle = RefreshThrottle()
    val now = Instant.now()

    // Record refresh
    throttle.recordRefresh("IW-1", now)

    // Try to refresh 60 seconds later
    val later = now.plus(60, ChronoUnit.SECONDS)
    assert(throttle.shouldRefresh("IW-1", later))
  }

  test("each worktree tracked independently") {
    val throttle = RefreshThrottle()
    val now = Instant.now()

    // Refresh IW-1
    throttle.recordRefresh("IW-1", now)

    // IW-1 should be throttled
    val later = now.plus(10, ChronoUnit.SECONDS)
    assert(!throttle.shouldRefresh("IW-1", later))

    // But IW-2 should not be throttled
    assert(throttle.shouldRefresh("IW-2", later))
  }

  test("recordRefresh updates the last refresh time") {
    val throttle = RefreshThrottle()
    val now = Instant.now()

    // First refresh
    throttle.recordRefresh("IW-1", now)
    assert(!throttle.shouldRefresh("IW-1", now.plus(10, ChronoUnit.SECONDS)))

    // Wait 40 seconds and refresh again
    val later = now.plus(40, ChronoUnit.SECONDS)
    throttle.recordRefresh("IW-1", later)

    // Should be throttled again (from the new timestamp)
    val evenLater = later.plus(10, ChronoUnit.SECONDS)
    assert(!throttle.shouldRefresh("IW-1", evenLater))
  }
