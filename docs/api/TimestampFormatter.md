# TimestampFormatter

> Format timestamps as human-readable relative time strings.

## Import

```scala
import iw.core.output.*
import java.time.Instant
```

## API

### TimestampFormatter.formatUpdateTimestamp(timestamp: Instant, now: Instant): String

Format a timestamp as relative time from a reference point.

Returns:
- `"Updated just now"` for < 30 seconds
- `"Updated X seconds ago"` for 30-59 seconds
- `"Updated 1 minute ago"` for 60-119 seconds
- `"Updated X minutes ago"` for 2-59 minutes
- `"Updated 1 hour ago"` for 60-119 minutes
- `"Updated X hours ago"` for >= 2 hours

## Examples

```scala
import java.time.Instant
import java.time.Duration

val now = Instant.now()

// Just now
TimestampFormatter.formatUpdateTimestamp(now, now)
// "Updated just now"

// 45 seconds ago
val ts1 = now.minus(Duration.ofSeconds(45))
TimestampFormatter.formatUpdateTimestamp(ts1, now)
// "Updated 45 seconds ago"

// 5 minutes ago
val ts2 = now.minus(Duration.ofMinutes(5))
TimestampFormatter.formatUpdateTimestamp(ts2, now)
// "Updated 5 minutes ago"

// 3 hours ago
val ts3 = now.minus(Duration.ofHours(3))
TimestampFormatter.formatUpdateTimestamp(ts3, now)
// "Updated 3 hours ago"

// Usage in dashboard display
val lastUpdated = worktree.lastSeenAt
Output.info(TimestampFormatter.formatUpdateTimestamp(lastUpdated, Instant.now()))
```

## Notes

- Designed for dashboard/status display showing data freshness
- Always produces "Updated X ago" format
- Grammatically correct singular/plural handling
