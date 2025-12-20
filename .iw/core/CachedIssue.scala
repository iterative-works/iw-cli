// PURPOSE: Cache wrapper for issue data with TTL validation
// PURPOSE: Provides pure functions for checking cache validity and age

package iw.core.domain

import java.time.{Duration, Instant}

/** Default TTL for issue cache in minutes */
val DEFAULT_ISSUE_CACHE_TTL_MINUTES = 5

/** Cached issue data with TTL validation.
  *
  * @param data Issue data from tracker
  * @param ttlMinutes Time-to-live in minutes (default: 5)
  */
case class CachedIssue(
  data: IssueData,
  ttlMinutes: Int = DEFAULT_ISSUE_CACHE_TTL_MINUTES
)

object CachedIssue:
  /** Check if cached issue is still valid based on TTL.
    *
    * Cache is valid when: age < ttlMinutes
    * Cache is invalid when: age >= ttlMinutes
    *
    * @param cached Cached issue to validate
    * @param now Current timestamp for age calculation
    * @return true if cache is valid, false if expired
    */
  def isValid(cached: CachedIssue, now: Instant): Boolean =
    val ageInMinutes = Duration.between(cached.data.fetchedAt, now).toMinutes
    ageInMinutes < cached.ttlMinutes

  /** Calculate age of cached issue.
    *
    * @param cached Cached issue
    * @param now Current timestamp
    * @return Duration since issue was fetched
    */
  def age(cached: CachedIssue, now: Instant): Duration =
    Duration.between(cached.data.fetchedAt, now)
