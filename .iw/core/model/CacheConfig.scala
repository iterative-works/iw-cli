// PURPOSE: Configuration for cache TTL values with environment variable support
// PURPOSE: Pure domain config that parses TTL values from an environment map

package iw.core.model

/** Configuration for cache time-to-live (TTL) values.
  *
  * TTL values control how long cached data is considered fresh before
  * it should be refreshed from the API.
  *
  * @param env Environment variables map for configuration
  */
case class CacheConfig(env: Map[String, String]):

  /** Issue cache TTL in minutes.
    *
    * Configurable via IW_ISSUE_CACHE_TTL_MINUTES environment variable.
    * Default: 30 minutes (longer than Phase 1's 5 minutes for aggressive caching)
    *
    * @return TTL in minutes (always positive)
    */
  def issueCacheTTL: Int =
    env.get("IW_ISSUE_CACHE_TTL_MINUTES")
      .flatMap(s => s.toIntOption)
      .filter(_ > 0)
      .getOrElse(30)

  /** Pull request cache TTL in minutes.
    *
    * Configurable via IW_PR_CACHE_TTL_MINUTES environment variable.
    * Default: 15 minutes (longer than Phase 1's 2 minutes for aggressive caching)
    *
    * @return TTL in minutes (always positive)
    */
  def prCacheTTL: Int =
    env.get("IW_PR_CACHE_TTL_MINUTES")
      .flatMap(s => s.toIntOption)
      .filter(_ > 0)
      .getOrElse(15)

object CacheConfig:
  /** Default configuration for cases where no environment customization is needed */
  val Default: CacheConfig = CacheConfig(Map.empty)
