// PURPOSE: View model combining pull request data with a staleness flag
// PURPOSE: Used by card renderers to display PR state and stale-cache indicators

package iw.dashboard.presentation.views

import iw.core.model.{CachedPR, PullRequestData}

import java.time.Instant

/** PR data enriched with a staleness flag for the card renderer.
  *
  * @param pr
  *   Raw pull request data (URL, state, number, title)
  * @param isStale
  *   True when the underlying cache entry has exceeded its TTL
  */
final case class PrDisplayData(
    pr: PullRequestData,
    isStale: Boolean
)

object PrDisplayData:
  /** Build a `PrDisplayData` from a cached PR entry, deriving staleness from
    * the cache's TTL.
    */
  def fromCached(cached: CachedPR, now: Instant): PrDisplayData =
    PrDisplayData(cached.pr, isStale = CachedPR.isStale(cached, now))
