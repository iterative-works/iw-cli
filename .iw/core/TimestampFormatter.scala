// PURPOSE: Utility for formatting timestamps as relative time strings
// PURPOSE: Provides human-readable update timestamps like "Updated 5 minutes ago"

package iw.core.presentation.views

import java.time.Instant
import java.time.Duration

object TimestampFormatter:
  /** Format a timestamp as relative time from now.
    *
    * Returns:
    * - "Updated just now" for < 30s ago
    * - "Updated X seconds ago" for 30s-59s
    * - "Updated 1 minute ago" for 60s-119s
    * - "Updated X minutes ago" for 2-59 minutes
    * - "Updated 1 hour ago" for 60-119 minutes
    * - "Updated X hours ago" for >= 2 hours
    *
    * @param timestamp When the data was last updated
    * @param now Current timestamp
    * @return Formatted string
    */
  def formatUpdateTimestamp(timestamp: Instant, now: Instant): String =
    val duration = Duration.between(timestamp, now)
    val seconds = duration.getSeconds

    if seconds < 30 then
      "Updated just now"
    else if seconds < 60 then
      s"Updated $seconds seconds ago"
    else
      val minutes = seconds / 60
      if minutes < 2 then
        "Updated 1 minute ago"
      else if minutes < 60 then
        s"Updated $minutes minutes ago"
      else
        val hours = minutes / 60
        if hours < 2 then
          "Updated 1 hour ago"
        else
          s"Updated $hours hours ago"
