// PURPOSE: Cache wrapper for issue data with TTL validation
// PURPOSE: Provides pure functions for checking cache validity and age

package iw.core.model

import java.time.{Duration, Instant}
import iw.core.model.CachedIssue
import iw.core.model.Issue
import iw.core.model.IssueData

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

  /** Check if cached issue is stale (age >= TTL).
    *
    * Stale data can still be displayed with an indicator.
    * This is different from isValid() - stale data should show a visual indicator.
    *
    * @param cached Cached issue to check
    * @param now Current timestamp for age calculation
    * @return true if cache is stale (age >= TTL), false otherwise
    */
  def isStale(cached: CachedIssue, now: Instant): Boolean =
    val ageInMinutes = Duration.between(cached.data.fetchedAt, now).toMinutes
    ageInMinutes >= cached.ttlMinutes
