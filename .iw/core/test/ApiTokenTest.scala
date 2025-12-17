// PURPOSE: Tests for ApiToken opaque type
// PURPOSE: Verifies secure handling of API tokens with masked toString
package iw.tests

import iw.core.*

class ApiTokenTest extends munit.FunSuite:

  test("ApiToken.apply creates token from non-empty string"):
    val token = ApiToken("lin_api_abc123xyz")
    assert(token.isDefined)
    assertEquals(token.get.value, "lin_api_abc123xyz")

  test("ApiToken.apply returns None for empty string"):
    val token = ApiToken("")
    assert(token.isEmpty)

  test("ApiToken.apply returns None for whitespace-only string"):
    val token = ApiToken("   ")
    assert(token.isEmpty)

  test("ApiToken.value returns the actual token value"):
    val token = ApiToken("secret_token_value").get
    assertEquals(token.value, "secret_token_value")

  test("ApiToken.toString masks the value for safe logging"):
    val token = ApiToken("lin_api_abc123xyz").get
    val stringRepr = token.toString
    // Should NOT contain the actual token value
    assert(!stringRepr.contains("lin_api_abc123xyz"))
    // Should indicate it's an API token
    assert(stringRepr.contains("ApiToken"))
    // Should be masked
    assert(stringRepr.contains("***"))

  test("ApiToken.toString shows partial token for debugging"):
    val token = ApiToken("lin_api_abc123xyz789").get
    val stringRepr = token.toString
    // Should show first few chars for debugging
    assert(stringRepr.contains("lin_"))

  test("ApiToken.isEmpty returns false for valid token"):
    val token = ApiToken("valid_token").get
    assert(!token.isEmpty)

  test("ApiToken short token masks appropriately"):
    val token = ApiToken("abc").get
    val stringRepr = token.toString
    // Short tokens should still be masked
    assert(!stringRepr.contains("abc") || stringRepr.contains("***"))

  test("ApiToken fromEnv reads from environment variable"):
    // This test verifies the API, actual env var testing done in integration
    val result = ApiToken.fromEnv("NONEXISTENT_VAR_12345")
    assert(result.isEmpty)
