// PURPOSE: Unit tests for DevModeConfig.resolve — the double-gated dev mode resolver
// PURPOSE: Covers all branches: flag-off, flag-alone, env-alone, valid loopback, invalid inputs

package iw.core.test

import iw.dashboard.DevModeConfig

class DevModeConfigTest extends munit.FunSuite:

  test("resolve(false, None) returns Right(Off) — flag off regardless of env"):
    assertEquals(
      DevModeConfig.resolve(devFlag = false, viteDevUrlEnv = None),
      Right(DevModeConfig.Off)
    )

  test(
    "resolve(false, Some(url)) returns Right(Off) — env var alone never activates"
  ):
    assertEquals(
      DevModeConfig.resolve(
        devFlag = false,
        viteDevUrlEnv = Some("http://localhost:5173")
      ),
      Right(DevModeConfig.Off)
    )

  test("resolve(true, None) returns Right(Off) — flag alone never activates"):
    assertEquals(
      DevModeConfig.resolve(devFlag = true, viteDevUrlEnv = None),
      Right(DevModeConfig.Off)
    )

  test(
    "resolve(true, Some('')) returns Right(Off) — empty string treated as unset"
  ):
    assertEquals(
      DevModeConfig.resolve(devFlag = true, viteDevUrlEnv = Some("")),
      Right(DevModeConfig.Off)
    )

  test(
    "resolve(true, Some(localhost url)) returns Right(On(...)) — happy path"
  ):
    assertEquals(
      DevModeConfig.resolve(
        devFlag = true,
        viteDevUrlEnv = Some("http://localhost:5173")
      ),
      Right(DevModeConfig.On("http://localhost:5173"))
    )

  test(
    "resolve(true, Some(127.0.0.1 url)) returns Right(On(...)) — IPv4 loopback accepted"
  ):
    assertEquals(
      DevModeConfig.resolve(
        devFlag = true,
        viteDevUrlEnv = Some("http://127.0.0.1:5173")
      ),
      Right(DevModeConfig.On("http://127.0.0.1:5173"))
    )

  test("resolve(true, IPv6 loopback) returns Left — IPv6 rejected per DM-IPV6"):
    val result = DevModeConfig.resolve(
      devFlag = true,
      viteDevUrlEnv = Some("http://[::1]:5173")
    )
    assert(result.isLeft, s"Expected Left but got $result")
    assert(
      result.left.exists(_.contains("loopback")),
      s"Error should mention loopback, got: ${result.left}"
    )

  test("resolve(true, https scheme) returns Left — scheme must be http"):
    val result = DevModeConfig.resolve(
      devFlag = true,
      viteDevUrlEnv = Some("https://localhost:5173")
    )
    assert(result.isLeft, s"Expected Left but got $result")
    assert(
      result.left.exists(_.contains("scheme")),
      s"Error should mention scheme, got: ${result.left}"
    )
    assert(
      result.left.exists(_.contains("http")),
      s"Error should mention http, got: ${result.left}"
    )

  test("resolve(true, non-loopback host) returns Left"):
    val result = DevModeConfig.resolve(
      devFlag = true,
      viteDevUrlEnv = Some("http://example.com:5173")
    )
    assert(result.isLeft, s"Expected Left but got $result")
    assert(
      result.left.exists(_.contains("loopback")),
      s"Error should mention loopback, got: ${result.left}"
    )

  test("resolve(true, private IP) returns Left — private IP is non-loopback"):
    val result = DevModeConfig.resolve(
      devFlag = true,
      viteDevUrlEnv = Some("http://10.0.0.5:5173")
    )
    assert(result.isLeft, s"Expected Left but got $result")

  test(
    "resolve(true, localhost.example.com) returns Left — exact equality, not substring"
  ):
    val result = DevModeConfig.resolve(
      devFlag = true,
      viteDevUrlEnv = Some("http://localhost.example.com:5173")
    )
    assert(result.isLeft, s"Expected Left but got $result")
    assert(
      result.left.exists(_.contains("loopback")),
      s"Error should mention loopback, got: ${result.left}"
    )

  test("resolve(true, malformed URI) returns Left"):
    val result = DevModeConfig.resolve(
      devFlag = true,
      viteDevUrlEnv = Some("not a url")
    )
    assert(result.isLeft, s"Expected Left but got $result")
