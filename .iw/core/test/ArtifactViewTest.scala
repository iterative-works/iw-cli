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

  // Mermaid.js integration tests
  test("render includes mermaid.js script tag from CDN"):
    val html = ArtifactView.render(
      artifactLabel = "test.md",
      renderedHtml = "<p>Content</p>",
      issueId = "TEST-1"
    )

    assert(html.contains("<script src=\"https://cdn.jsdelivr.net/npm/mermaid@10.9.4/dist/mermaid.min.js\"></script>"))

  test("script tag uses correct version v10.9.4"):
    val html = ArtifactView.render(
      artifactLabel = "test.md",
      renderedHtml = "<p>Content</p>",
      issueId = "TEST-1"
    )

    assert(html.contains("mermaid@10.9.4"))

  test("render includes mermaid initialization script"):
    val html = ArtifactView.render(
      artifactLabel = "test.md",
      renderedHtml = "<p>Content</p>",
      issueId = "TEST-1"
    )

    assert(html.contains("mermaid.initialize"))

  test("initialization script configures neutral theme"):
    val html = ArtifactView.render(
      artifactLabel = "test.md",
      renderedHtml = "<p>Content</p>",
      issueId = "TEST-1"
    )

    assert(html.contains("theme: 'neutral'"))

  test("initialization script sets startOnLoad false for custom error handling"):
    val html = ArtifactView.render(
      artifactLabel = "test.md",
      renderedHtml = "<p>Content</p>",
      issueId = "TEST-1"
    )

    // startOnLoad is false because we use custom rendering with detailed error messages
    assert(html.contains("startOnLoad: false"))

  test("mermaid scripts are in head section"):
    val html = ArtifactView.render(
      artifactLabel = "test.md",
      renderedHtml = "<p>Content</p>",
      issueId = "TEST-1"
    )

    val headEnd = html.indexOf("</head>")
    val mermaidScriptPos = html.indexOf("mermaid@10.9.4")
    val mermaidInitPos = html.indexOf("mermaid.initialize")

    assert(headEnd > 0, "Should have closing </head> tag")
    assert(mermaidScriptPos > 0, "Should have mermaid script")
    assert(mermaidInitPos > 0, "Should have mermaid initialization")
    assert(mermaidScriptPos < headEnd, "Mermaid script should be in <head>")
    assert(mermaidInitPos < headEnd, "Mermaid init should be in <head>")

  // Mermaid error handling tests (Phase 2)
  test("mermaid initialization sets securityLevel to loose for error display"):
    val html = ArtifactView.render(
      artifactLabel = "test.md",
      renderedHtml = "<p>Content</p>",
      issueId = "TEST-1"
    )

    assert(html.contains("securityLevel: 'loose'"))

  test("CSS includes mermaid error styling"):
    val html = ArtifactView.render(
      artifactLabel = "test.md",
      renderedHtml = "<p>Content</p>",
      issueId = "TEST-1"
    )

    assert(html.contains(".mermaid"))
    // Error styling should target error elements
    assert(html.contains("error") || html.contains("Error"))
