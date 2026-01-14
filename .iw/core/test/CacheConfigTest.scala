// PURPOSE: Unit tests for CacheConfig with environment variable support
// PURPOSE: Tests configurable TTL values from environment or defaults

package iw.core.domain

import munit.FunSuite

class CacheConfigTest extends FunSuite:

  test("issueCacheTTL returns default value when env var not set"):
    val config = CacheConfig(Map.empty)

    assertEquals(config.issueCacheTTL, 30, "Default issue cache TTL should be 30 minutes")

  test("prCacheTTL returns default value when env var not set"):
    val config = CacheConfig(Map.empty)

    assertEquals(config.prCacheTTL, 15, "Default PR cache TTL should be 15 minutes")

  test("issueCacheTTL reads from IW_ISSUE_CACHE_TTL_MINUTES env var"):
    val env = Map("IW_ISSUE_CACHE_TTL_MINUTES" -> "60")
    val config = CacheConfig(env)

    assertEquals(config.issueCacheTTL, 60)

  test("prCacheTTL reads from IW_PR_CACHE_TTL_MINUTES env var"):
    val env = Map("IW_PR_CACHE_TTL_MINUTES" -> "10")
    val config = CacheConfig(env)

    assertEquals(config.prCacheTTL, 10)

  test("issueCacheTTL falls back to default on invalid env var"):
    val env = Map("IW_ISSUE_CACHE_TTL_MINUTES" -> "not-a-number")
    val config = CacheConfig(env)

    assertEquals(config.issueCacheTTL, 30, "Should use default when env var is invalid")

  test("prCacheTTL falls back to default on invalid env var"):
    val env = Map("IW_PR_CACHE_TTL_MINUTES" -> "invalid")
    val config = CacheConfig(env)

    assertEquals(config.prCacheTTL, 15, "Should use default when env var is invalid")

  test("issueCacheTTL falls back to default on negative value"):
    val env = Map("IW_ISSUE_CACHE_TTL_MINUTES" -> "-5")
    val config = CacheConfig(env)

    assertEquals(config.issueCacheTTL, 30, "Should reject negative TTL values")

  test("prCacheTTL falls back to default on zero value"):
    val env = Map("IW_PR_CACHE_TTL_MINUTES" -> "0")
    val config = CacheConfig(env)

    assertEquals(config.prCacheTTL, 15, "Should reject zero TTL values")

  test("CacheConfig.Default uses default values"):
    val config = CacheConfig.Default

    assertEquals(config.issueCacheTTL, 30, "Default issue cache TTL should be 30 minutes")
    assertEquals(config.prCacheTTL, 15, "Default PR cache TTL should be 15 minutes")
