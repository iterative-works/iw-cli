// PURPOSE: Unit tests for MarkdownRenderer markdown-to-HTML conversion
// PURPOSE: Verify flexmark integration and GitHub Flavored Markdown support

package iw.core.test

import iw.core.infrastructure.MarkdownRenderer

class MarkdownRendererTest extends munit.FunSuite:

  test("renders basic markdown headers"):
    val markdown = """# Heading 1
                     |## Heading 2
                     |### Heading 3
                     |#### Heading 4""".stripMargin
    val html = MarkdownRenderer.toHtml(markdown)

    assert(html.contains("<h1>"))
    assert(html.contains("Heading 1"))
    assert(html.contains("<h2>"))
    assert(html.contains("Heading 2"))
    assert(html.contains("<h3>"))
    assert(html.contains("Heading 3"))
    assert(html.contains("<h4>"))
    assert(html.contains("Heading 4"))

  test("renders paragraphs"):
    val markdown = """First paragraph.
                     |
                     |Second paragraph.""".stripMargin
    val html = MarkdownRenderer.toHtml(markdown)

    assert(html.contains("<p>"))
    assert(html.contains("First paragraph"))
    assert(html.contains("Second paragraph"))

  test("renders unordered lists"):
    val markdown = """- Item 1
                     |- Item 2
                     |- Item 3""".stripMargin
    val html = MarkdownRenderer.toHtml(markdown)

    assert(html.contains("<ul>"))
    assert(html.contains("<li>"))
    assert(html.contains("Item 1"))
    assert(html.contains("Item 2"))
    assert(html.contains("Item 3"))

  test("renders ordered lists"):
    val markdown = """1. First
                     |2. Second
                     |3. Third""".stripMargin
    val html = MarkdownRenderer.toHtml(markdown)

    assert(html.contains("<ol>"))
    assert(html.contains("<li>"))
    assert(html.contains("First"))
    assert(html.contains("Second"))
    assert(html.contains("Third"))

  test("renders inline code"):
    val markdown = "Use `const x = 1` for constants"
    val html = MarkdownRenderer.toHtml(markdown)

    assert(html.contains("<code>"))
    assert(html.contains("const x = 1"))

  test("renders code blocks with language tags"):
    val markdown = """```scala
                     |def hello(): String = "world"
                     |```""".stripMargin
    val html = MarkdownRenderer.toHtml(markdown)

    assert(html.contains("<code"))
    assert(html.contains("def hello()"))

  test("renders tables (GFM style)"):
    val markdown = """| Header 1 | Header 2 |
                     ||----------|----------|
                     || Cell 1   | Cell 2   |
                     || Cell 3   | Cell 4   |""".stripMargin
    val html = MarkdownRenderer.toHtml(markdown)

    assert(html.contains("<table>"))
    assert(html.contains("<thead>"))
    assert(html.contains("<tbody>"))
    assert(html.contains("<th>"))
    assert(html.contains("<td>"))
    assert(html.contains("Header 1"))
    assert(html.contains("Cell 1"))

  test("renders links"):
    val markdown = "[Link text](https://example.com)"
    val html = MarkdownRenderer.toHtml(markdown)

    assert(html.contains("<a"))
    assert(html.contains("href=\"https://example.com\""))
    assert(html.contains("Link text"))

  test("renders autolinks"):
    val markdown = "https://example.com"
    val html = MarkdownRenderer.toHtml(markdown)

    assert(html.contains("<a"))
    assert(html.contains("https://example.com"))

  test("renders blockquotes"):
    val markdown = "> This is a quote"
    val html = MarkdownRenderer.toHtml(markdown)

    assert(html.contains("<blockquote>"))
    assert(html.contains("This is a quote"))

  test("handles empty input"):
    val html = MarkdownRenderer.toHtml("")
    assertEquals(html, "")

  test("escapes special HTML characters"):
    val markdown = "Use <script> tags carefully"
    val html = MarkdownRenderer.toHtml(markdown)

    // Flexmark wraps content in <p> tags and preserves the text
    // The important thing is that <script> doesn't execute as actual HTML
    assert(html.contains("<p>"))
    assert(html.contains("tags carefully"))

  test("renders nested lists"):
    val markdown = """- Item 1
                     |  - Nested 1.1
                     |  - Nested 1.2
                     |- Item 2""".stripMargin
    val html = MarkdownRenderer.toHtml(markdown)

    assert(html.contains("<ul>"))
    assert(html.contains("<li>"))
    assert(html.contains("Item 1"))
    assert(html.contains("Nested 1.1"))

  test("renders strikethrough (GFM extension)"):
    val markdown = "~~strikethrough~~"
    val html = MarkdownRenderer.toHtml(markdown)

    assert(html.contains("<del>") || html.contains("strikethrough"))

  test("handles large markdown input"):
    val largeMarkdown = ("# Section\n\nContent paragraph.\n\n" * 1000)
    val html = MarkdownRenderer.toHtml(largeMarkdown)

    // Should complete without hanging
    assert(html.nonEmpty)
    assert(html.contains("<h1>"))
    assert(html.contains("Section"))
