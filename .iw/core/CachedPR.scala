// PURPOSE: Cache wrapper for pull request data with TTL validation
// PURPOSE: Provides pure functions for checking cache validity and age

package iw.core.domain

import java.time.{Duration, Instant}

/** Cached pull request data with TTL validation.
  *
  * @param pr Pull request data from GitHub/GitLab
  * @param fetchedAt Timestamp when PR data was fetched
  */
case class CachedPR(
  pr: PullRequestData,
  fetchedAt: Instant
)

object CachedPR:
  /** TTL for PR cache in minutes.
    * PRs change more frequently than issues, so use shorter TTL (2 vs 5 minutes).
    */
  val TTL_MINUTES = 2

  /** Check if cached PR is still valid based on TTL.
    *
    * Cache is valid when: age < TTL_MINUTES
    * Cache is invalid when: age >= TTL_MINUTES
    *
    * @param cached Cached PR to validate
    * @param now Current timestamp for age calculation
    * @return true if cache is valid, false if expired
    */
  def isValid(cached: CachedPR, now: Instant): Boolean =
    val ageInMinutes = Duration.between(cached.fetchedAt, now).toMinutes
    ageInMinutes < TTL_MINUTES

  /** Calculate age of cached PR.
    *
    * @param cached Cached PR
    * @param now Current timestamp
    * @return Duration since PR was fetched
    */
  def age(cached: CachedPR, now: Instant): Duration =
    Duration.between(cached.fetchedAt, now)

  /** Check if cached PR is stale (age >= TTL).
    *
    * Stale data can still be displayed with an indicator.
    * This is different from isValid() - stale data should show a visual indicator.
    *
    * @param cached Cached PR to check
    * @param now Current timestamp for age calculation
    * @return true if cache is stale (age >= TTL), false otherwise
    */
  def isStale(cached: CachedPR, now: Instant): Boolean =
    val ageInMinutes = Duration.between(cached.fetchedAt, now).toMinutes
    ageInMinutes >= TTL_MINUTES
