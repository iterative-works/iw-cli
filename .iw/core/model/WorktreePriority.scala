// PURPOSE: Pure priority score calculation for worktrees based on activity timestamps
// PURPOSE: Determines refresh ordering for dashboard cards (higher score = higher priority)

package iw.core.model

import java.time.Instant
import java.time.Duration
import iw.core.model.WorktreeRegistration
import iw.core.model.WorktreePriority

object WorktreePriority:
  /** Calculate priority score for a worktree based on last activity timestamp.
    *
    * Score is based on time since last activity:
    * - More recent activity = higher score
    * - Older activity = lower score
    *
    * The score is calculated as the negative number of seconds since last activity,
    * so that sorting in descending order puts most recent first.
    *
    * @param registration Worktree registration with activity timestamp
    * @param now Current timestamp for comparison
    * @return Priority score (higher = more recent activity = higher priority)
    */
  def priorityScore(registration: WorktreeRegistration, now: Instant): Long =
    val secondsSinceActivity = Duration.between(registration.lastSeenAt, now).getSeconds
    // Return negative value so that more recent activity (smaller duration) gets higher score
    -secondsSinceActivity
