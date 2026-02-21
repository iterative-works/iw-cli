// PURPOSE: Unit tests for PageLayout presentation component
// PURPOSE: Verify HTML structure and rendering of shared page layout

package iw.core.test

import iw.core.dashboard.presentation.views.PageLayout
import scalatags.Text.all.*

class PageLayoutTest extends munit.FunSuite:

  test("render with title and empty body produces valid HTML5 document structure"):
    val html = PageLayout.render(
      title = "Test Page",
      bodyContent = div(),
      devMode = false
    )

    assert(html.startsWith("<!DOCTYPE html>"))
    assert(html.contains("<html>"))
    assert(html.contains("<head>"))
    assert(html.contains("<body"))
    assert(html.contains("</html>"))

  test("render includes DOCTYPE"):
    val html = PageLayout.render(
      title = "Test",
      bodyContent = div(),
      devMode = false
    )

    assert(html.startsWith("<!DOCTYPE html>"))

  test("render includes HTMX CDN scripts"):
    val html = PageLayout.render(
      title = "Test",
      bodyContent = div(),
      devMode = false
    )

    // Check for main HTMX script
    assert(html.contains("https://unpkg.com/htmx.org@1.9.10"))
    // Check for response-targets extension
    assert(html.contains("https://unpkg.com/htmx-ext-response-targets@2.0.0/response-targets.js"))

  test("render includes /static/dashboard.css link"):
    val html = PageLayout.render(
      title = "Test",
      bodyContent = div(),
      devMode = false
    )

    assert(html.contains("href=\"/static/dashboard.css\""))
    assert(html.contains("rel=\"stylesheet\""))

  test("render includes /static/dashboard.js script"):
    val html = PageLayout.render(
      title = "Test",
      bodyContent = div(),
      devMode = false
    )

    assert(html.contains("src=\"/static/dashboard.js\""))

  test("render wraps body content in container"):
    val content = div(cls := "test-content", "Test Content")
    val html = PageLayout.render(
      title = "Test",
      bodyContent = content,
      devMode = false
    )

    assert(html.contains("class=\"container\""))
    assert(html.contains("class=\"test-content\""))
    assert(html.contains("Test Content"))

  test("render includes dev mode banner when enabled"):
    val html = PageLayout.render(
      title = "Test",
      bodyContent = div(),
      devMode = true
    )

    assert(html.contains("dev-mode-banner"))
    assert(html.contains("DEV MODE"))

  test("render does not include dev mode banner when disabled"):
    val html = PageLayout.render(
      title = "Test",
      bodyContent = div(),
      devMode = false
    )

    assert(!html.contains("dev-mode-banner"))
    assert(!html.contains("DEV MODE"))

  test("render includes hx-ext attribute on body"):
    val html = PageLayout.render(
      title = "Test",
      bodyContent = div(),
      devMode = false
    )

    assert(html.contains("hx-ext=\"response-targets\""))

  test("render includes page title in head"):
    val html = PageLayout.render(
      title = "Dashboard - Test",
      bodyContent = div(),
      devMode = false
    )

    assert(html.contains("<title>Dashboard - Test</title>"))

  test("render includes meta charset"):
    val html = PageLayout.render(
      title = "Test",
      bodyContent = div(),
      devMode = false
    )

    assert(html.contains("charset="))

  test("render includes viewport meta tag"):
    val html = PageLayout.render(
      title = "Test",
      bodyContent = div(),
      devMode = false
    )

    assert(html.contains("viewport"))

  test("CSS link appears before JS script in head"):
    val html = PageLayout.render(
      title = "Test",
      bodyContent = div(),
      devMode = false
    )

    val cssPos = html.indexOf("/static/dashboard.css")
    val jsPos = html.indexOf("/static/dashboard.js")
    val headEnd = html.indexOf("</head>")

    assert(cssPos > 0, "CSS link should be present")
    assert(jsPos > 0, "JS script should be present")
    assert(cssPos < jsPos, "CSS should come before JS")
    assert(jsPos < headEnd, "JS should be in head")
