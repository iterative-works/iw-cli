// PURPOSE: Rate limiting for per-worktree background refresh operations
// PURPOSE: Prevents API hammering by enforcing 30s minimum interval between refreshes

package iw.core.dashboard

import java.time.Instant
import java.time.temporal.ChronoUnit
import scala.collection.mutable

class RefreshThrottle:
  private val lastRefreshTimes = mutable.Map[String, Instant]()
  private val throttleSeconds = 30L

  /** Check if a worktree should be refreshed.
    *
    * Returns true if:
    * - Worktree has never been refreshed
    * - Last refresh was >= 30s ago
    *
    * @param issueId Worktree issue ID
    * @param now Current timestamp
    * @return true if refresh should proceed, false if throttled
    */
  def shouldRefresh(issueId: String, now: Instant): Boolean =
    lastRefreshTimes.get(issueId) match
      case None =>
        true
      case Some(lastRefresh) =>
        val elapsed = ChronoUnit.SECONDS.between(lastRefresh, now)
        elapsed >= throttleSeconds

  /** Record that a worktree was refreshed at a specific time.
    *
    * @param issueId Worktree issue ID
    * @param timestamp When the refresh occurred
    */
  def recordRefresh(issueId: String, timestamp: Instant): Unit =
    lastRefreshTimes(issueId) = timestamp

object RefreshThrottle:
  def apply(): RefreshThrottle = new RefreshThrottle()
