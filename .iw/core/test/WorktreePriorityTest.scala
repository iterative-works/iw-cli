// PURPOSE: Unit tests for WorktreePriority priority score calculation
// PURPOSE: Verify recent activity gets higher priority and score calculation is deterministic

package iw.core.test

import iw.core.domain.{WorktreeRegistration, WorktreePriority}
import java.time.Instant

class WorktreePriorityTest extends munit.FunSuite:

  val now = Instant.parse("2025-01-15T12:00:00Z")

  test("recent activity gets higher priority score than older activity"):
    val recentWorktree = WorktreeRegistration(
      issueId = "IW-1",
      path = "/path/to/recent",
      trackerType = "Linear",
      team = "Engineering",
      registeredAt = Instant.parse("2025-01-10T10:00:00Z"),
      lastSeenAt = Instant.parse("2025-01-15T11:00:00Z") // 1 hour ago
    )

    val olderWorktree = WorktreeRegistration(
      issueId = "IW-2",
      path = "/path/to/older",
      trackerType = "Linear",
      team = "Engineering",
      registeredAt = Instant.parse("2025-01-10T10:00:00Z"),
      lastSeenAt = Instant.parse("2025-01-14T10:00:00Z") // 26 hours ago
    )

    val recentScore = WorktreePriority.priorityScore(recentWorktree, now)
    val olderScore = WorktreePriority.priorityScore(olderWorktree, now)

    assert(recentScore > olderScore, s"Recent activity (score=$recentScore) should have higher score than older (score=$olderScore)")

  test("older activity gets lower priority score"):
    val veryOldWorktree = WorktreeRegistration(
      issueId = "IW-3",
      path = "/path/to/very-old",
      trackerType = "Linear",
      team = "Engineering",
      registeredAt = Instant.parse("2025-01-01T10:00:00Z"),
      lastSeenAt = Instant.parse("2025-01-05T10:00:00Z") // 10 days ago
    )

    val somewhatOldWorktree = WorktreeRegistration(
      issueId = "IW-4",
      path = "/path/to/somewhat-old",
      trackerType = "Linear",
      team = "Engineering",
      registeredAt = Instant.parse("2025-01-10T10:00:00Z"),
      lastSeenAt = Instant.parse("2025-01-13T10:00:00Z") // 2 days ago
    )

    val veryOldScore = WorktreePriority.priorityScore(veryOldWorktree, now)
    val somewhatOldScore = WorktreePriority.priorityScore(somewhatOldWorktree, now)

    assert(somewhatOldScore > veryOldScore, s"Somewhat old activity (score=$somewhatOldScore) should have higher score than very old (score=$veryOldScore)")

  test("priority score is deterministic for same input"):
    val worktree = WorktreeRegistration(
      issueId = "IW-5",
      path = "/path/to/worktree",
      trackerType = "Linear",
      team = "Engineering",
      registeredAt = Instant.parse("2025-01-10T10:00:00Z"),
      lastSeenAt = Instant.parse("2025-01-15T10:00:00Z")
    )

    val score1 = WorktreePriority.priorityScore(worktree, now)
    val score2 = WorktreePriority.priorityScore(worktree, now)
    val score3 = WorktreePriority.priorityScore(worktree, now)

    assertEquals(score1, score2, "Priority score should be deterministic")
    assertEquals(score2, score3, "Priority score should be deterministic")

  test("priority score handles very recent activity (just now)"):
    val justNowWorktree = WorktreeRegistration(
      issueId = "IW-6",
      path = "/path/to/just-now",
      trackerType = "Linear",
      team = "Engineering",
      registeredAt = Instant.parse("2025-01-10T10:00:00Z"),
      lastSeenAt = now // Same as current time
    )

    val oneHourAgoWorktree = WorktreeRegistration(
      issueId = "IW-7",
      path = "/path/to/one-hour-ago",
      trackerType = "Linear",
      team = "Engineering",
      registeredAt = Instant.parse("2025-01-10T10:00:00Z"),
      lastSeenAt = Instant.parse("2025-01-15T11:00:00Z") // 1 hour ago
    )

    val justNowScore = WorktreePriority.priorityScore(justNowWorktree, now)
    val oneHourAgoScore = WorktreePriority.priorityScore(oneHourAgoWorktree, now)

    assert(justNowScore >= oneHourAgoScore, s"Just now activity (score=$justNowScore) should have at least as high score as 1 hour ago (score=$oneHourAgoScore)")
