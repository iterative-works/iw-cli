// PURPOSE: Configuration for cache TTL values with environment variable support
// PURPOSE: Provides default values and reads from environment variables

package iw.core.domain

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
  /** Create CacheConfig from system environment variables.
    *
    * @return CacheConfig instance using system environment
    */
  def fromSystemEnv(): CacheConfig =
    import scala.jdk.CollectionConverters._
    val env = System.getenv().asScala.toMap
    CacheConfig(env)
