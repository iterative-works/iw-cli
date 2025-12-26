// PURPOSE: Unit tests for ArtifactView presentation layer
// PURPOSE: Verify HTML structure and rendering of artifact viewing page

package iw.core.test

import iw.core.presentation.views.ArtifactView

class ArtifactViewTest extends munit.FunSuite:

  test("render produces valid HTML5 document structure"):
    val html = ArtifactView.render(
      artifactLabel = "analysis.md",
      renderedHtml = "<h1>Test</h1>",
      issueId = "TEST-123"
    )

    assert(html.startsWith("<!DOCTYPE html>"))
    assert(html.contains("<html>"))
    assert(html.contains("<head>"))
    assert(html.contains("<body>"))
    assert(html.contains("</html>"))

  test("render includes artifact label in title"):
    val html = ArtifactView.render(
      artifactLabel = "analysis.md",
      renderedHtml = "<p>Content</p>",
      issueId = "TEST-123"
    )

    assert(html.contains("<title>analysis.md - TEST-123</title>"))

  test("render includes artifact label in h1"):
    val html = ArtifactView.render(
      artifactLabel = "analysis.md",
      renderedHtml = "<p>Content</p>",
      issueId = "TEST-123"
    )

    assert(html.contains("<h1>analysis.md</h1>"))

  test("render includes issueId in subtitle"):
    val html = ArtifactView.render(
      artifactLabel = "analysis.md",
      renderedHtml = "<p>Content</p>",
      issueId = "TEST-123"
    )

    assert(html.contains("TEST-123"))
    assert(html.contains("class=\"issue-id\""))

  test("render back link points to dashboard"):
    val html = ArtifactView.render(
      artifactLabel = "analysis.md",
      renderedHtml = "<p>Content</p>",
      issueId = "TEST-123"
    )

    assert(html.contains("href=\"/\""))
    assert(html.contains("Back to Dashboard"))

  test("render includes rendered HTML in content div"):
    val renderedContent = "<h1>Heading</h1><p>Paragraph</p>"
    val html = ArtifactView.render(
      artifactLabel = "analysis.md",
      renderedHtml = renderedContent,
      issueId = "TEST-123"
    )

    assert(html.contains("<h1>Heading</h1>"))
    assert(html.contains("<p>Paragraph</p>"))
    assert(html.contains("class=\"content\""))

  test("render includes CSS styles"):
    val html = ArtifactView.render(
      artifactLabel = "analysis.md",
      renderedHtml = "<p>Content</p>",
      issueId = "TEST-123"
    )

    assert(html.contains("<style>"))
    assert(html.contains(".container"))
    assert(html.contains(".content"))
    assert(html.contains("</style>"))

  test("renderError produces error page structure"):
    val html = ArtifactView.renderError(
      issueId = "TEST-123",
      errorMessage = "Artifact not found"
    )

    assert(html.startsWith("<!DOCTYPE html>"))
    assert(html.contains("<html>"))
    assert(html.contains("Artifact Not Found"))
    assert(html.contains("Artifact not found"))

  test("renderError includes back link to dashboard"):
    val html = ArtifactView.renderError(
      issueId = "TEST-123",
      errorMessage = "File error"
    )

    assert(html.contains("href=\"/\""))
    assert(html.contains("Back to Dashboard"))

  test("renderError displays error message"):
    val html = ArtifactView.renderError(
      issueId = "TEST-123",
      errorMessage = "Custom error message"
    )

    assert(html.contains("Custom error message"))
    assert(html.contains("Unable to load artifact"))

  test("renderError includes return link"):
    val html = ArtifactView.renderError(
      issueId = "TEST-123",
      errorMessage = "Error"
    )

    assert(html.contains("Return to dashboard"))

  test("render handles special characters in labels"):
    val html = ArtifactView.render(
      artifactLabel = "phase-03-context.md",
      renderedHtml = "<p>Content</p>",
      issueId = "IWLE-72"
    )

    assert(html.contains("phase-03-context.md"))
    assert(html.contains("IWLE-72"))
