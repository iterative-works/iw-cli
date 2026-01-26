// PURPOSE: Unit tests for timestamp formatting utility
// PURPOSE: Validates relative time formatting for refresh timestamps

package iw.tests

import iw.core.output.TimestampFormatter
import java.time.Instant
import java.time.temporal.ChronoUnit

class TimestampFormatterTest extends munit.FunSuite:
  test("formatUpdateTimestamp returns 'Updated just now' for < 30s ago") {
    val now = Instant.now()
    val timestamp = now.minus(10, ChronoUnit.SECONDS)

    assertEquals(
      TimestampFormatter.formatUpdateTimestamp(timestamp, now),
      "Updated just now"
    )
  }

  test("formatUpdateTimestamp returns 'Updated just now' for exactly 0s ago") {
    val now = Instant.now()

    assertEquals(
      TimestampFormatter.formatUpdateTimestamp(now, now),
      "Updated just now"
    )
  }

  test("formatUpdateTimestamp returns 'Updated X seconds ago' for 30s-60s") {
    val now = Instant.now()
    val timestamp = now.minus(45, ChronoUnit.SECONDS)

    assertEquals(
      TimestampFormatter.formatUpdateTimestamp(timestamp, now),
      "Updated 45 seconds ago"
    )
  }

  test("formatUpdateTimestamp returns 'Updated X seconds ago' for exactly 30s") {
    val now = Instant.now()
    val timestamp = now.minus(30, ChronoUnit.SECONDS)

    assertEquals(
      TimestampFormatter.formatUpdateTimestamp(timestamp, now),
      "Updated 30 seconds ago"
    )
  }

  test("formatUpdateTimestamp returns 'Updated X seconds ago' for exactly 59s") {
    val now = Instant.now()
    val timestamp = now.minus(59, ChronoUnit.SECONDS)

    assertEquals(
      TimestampFormatter.formatUpdateTimestamp(timestamp, now),
      "Updated 59 seconds ago"
    )
  }

  test("formatUpdateTimestamp returns 'Updated 1 minute ago' for 60s-119s") {
    val now = Instant.now()
    val timestamp = now.minus(90, ChronoUnit.SECONDS)

    assertEquals(
      TimestampFormatter.formatUpdateTimestamp(timestamp, now),
      "Updated 1 minute ago"
    )
  }

  test("formatUpdateTimestamp returns 'Updated X minutes ago' for 2-59 minutes") {
    val now = Instant.now()
    val timestamp = now.minus(45, ChronoUnit.MINUTES)

    assertEquals(
      TimestampFormatter.formatUpdateTimestamp(timestamp, now),
      "Updated 45 minutes ago"
    )
  }

  test("formatUpdateTimestamp returns 'Updated 1 hour ago' for 60-119 minutes") {
    val now = Instant.now()
    val timestamp = now.minus(90, ChronoUnit.MINUTES)

    assertEquals(
      TimestampFormatter.formatUpdateTimestamp(timestamp, now),
      "Updated 1 hour ago"
    )
  }

  test("formatUpdateTimestamp returns 'Updated X hours ago' for > 2 hours") {
    val now = Instant.now()
    val timestamp = now.minus(5, ChronoUnit.HOURS)

    assertEquals(
      TimestampFormatter.formatUpdateTimestamp(timestamp, now),
      "Updated 5 hours ago"
    )
  }

  test("formatUpdateTimestamp handles 24 hours (1 day)") {
    val now = Instant.now()
    val timestamp = now.minus(24, ChronoUnit.HOURS)

    assertEquals(
      TimestampFormatter.formatUpdateTimestamp(timestamp, now),
      "Updated 24 hours ago"
    )
  }
