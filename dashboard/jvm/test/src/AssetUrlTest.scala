// PURPOSE: Unit tests for AssetUrl helper and AssetContext configuration
// PURPOSE: Verifies prod and dev mode asset URL generation with edge cases

package iw.core.test

import iw.dashboard.DevModeConfig
import iw.dashboard.presentation.views.{AssetUrl, AssetContext}

class AssetUrlTest extends munit.FunSuite:

  test("AssetUrl in prod mode returns /assets/<path>"):
    assertEquals(
      AssetUrl("main.js", AssetContext.prod),
      "/assets/main.js"
    )

  test("AssetUrl in dev mode returns Vite dev server URL with /src/ prefix"):
    val ctx = AssetContext(DevModeConfig.On("http://localhost:5173"))
    assertEquals(
      AssetUrl("main.js", ctx),
      "http://localhost:5173/src/main.js"
    )

  test("AssetUrl in dev mode strips trailing slash from base URL"):
    val ctx = AssetContext(DevModeConfig.On("http://localhost:5173/"))
    assertEquals(
      AssetUrl("main.js", ctx),
      "http://localhost:5173/src/main.js"
    )

  test("AssetUrl with subdirectory path in prod mode"):
    assertEquals(
      AssetUrl("components/foo.js", AssetContext.prod),
      "/assets/components/foo.js"
    )

  test("AssetUrl with subdirectory path in dev mode"):
    val ctx = AssetContext(DevModeConfig.On("http://localhost:5173"))
    assertEquals(
      AssetUrl("components/foo.js", ctx),
      "http://localhost:5173/src/components/foo.js"
    )
