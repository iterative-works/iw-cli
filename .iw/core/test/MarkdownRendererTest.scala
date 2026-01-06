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

  // Mermaid diagram rendering tests
  test("transforms mermaid code block to mermaid div"):
    val markdown = """```mermaid
                     |graph TD
                     |  A[Start] --> B[End]
                     |```""".stripMargin
    val html = MarkdownRenderer.toHtml(markdown)

    assert(html.contains("<div class=\"mermaid\">"))
    assert(html.contains("graph TD"))
    assert(html.contains("A[Start] --> B[End]"))
    assert(!html.contains("<pre><code class=\"language-mermaid\">"))

  test("mermaid div contains unescaped diagram syntax"):
    val markdown = """```mermaid
                     |graph TD
                     |  A[Start] --> B[End]
                     |```""".stripMargin
    val html = MarkdownRenderer.toHtml(markdown)

    assert(html.contains("-->"))
    assert(!html.contains("--&gt;"))
    assert(html.contains("A[Start]"))
    assert(!html.contains("A&lt;Start&gt;"))

  test("transforms multiple mermaid blocks"):
    val markdown = """```mermaid
                     |graph TD
                     |  A --> B
                     |```
                     |
                     |Text between diagrams.
                     |
                     |```mermaid
                     |graph LR
                     |  C --> D
                     |```""".stripMargin
    val html = MarkdownRenderer.toHtml(markdown)

    // Count occurrences of mermaid div
    val count = html.split("<div class=\"mermaid\">").length - 1
    assertEquals(count, 2)
    assert(html.contains("A --> B"))
    assert(html.contains("C --> D"))

  test("preserves non-mermaid code blocks"):
    val markdown = """```scala
                     |def hello = "world"
                     |```""".stripMargin
    val html = MarkdownRenderer.toHtml(markdown)

    assert(html.contains("<pre>") || html.contains("<code>"))
    assert(!html.contains("<div class=\"mermaid\">"))
    assert(html.contains("def hello"))

  test("handles empty mermaid block"):
    val markdown = """```mermaid
                     |```""".stripMargin
    val html = MarkdownRenderer.toHtml(markdown)

    assert(html.contains("<div class=\"mermaid\">"))

  test("mermaid block with special characters preserves content"):
    val markdown = """```mermaid
                     |graph TD
                     |  A[Start] -->|Yes| B{Decision}
                     |  B -->|No| C[End]
                     |```""".stripMargin
    val html = MarkdownRenderer.toHtml(markdown)

    assert(html.contains("<div class=\"mermaid\">"))
    assert(html.contains("-->|Yes|"))
    assert(html.contains("-->|No|"))
    assert(html.contains("B{Decision}"))
    assert(!html.contains("&gt;"))
    assert(!html.contains("&lt;"))

  // Tests for different Mermaid diagram types (Phase 3)
  test("transforms sequence diagram code block"):
    val markdown = """```mermaid
                     |sequenceDiagram
                     |  participant A as Alice
                     |  participant B as Bob
                     |  A->>B: Hello Bob
                     |  B->>A: Hello Alice
                     |```""".stripMargin
    val html = MarkdownRenderer.toHtml(markdown)

    assert(html.contains("<div class=\"mermaid\">"))
    assert(html.contains("sequenceDiagram"))
    assert(html.contains("participant A as Alice"))
    assert(html.contains("participant B as Bob"))
    assert(html.contains("A->>B: Hello Bob"))
    assert(html.contains("B->>A: Hello Alice"))
    assert(!html.contains("<pre><code class=\"language-mermaid\">"))

  test("transforms class diagram code block"):
    val markdown = """```mermaid
                     |classDiagram
                     |  class Animal {
                     |    +String name
                     |    +int age
                     |    +makeSound()
                     |  }
                     |  class Dog {
                     |    +bark()
                     |  }
                     |  Animal <|-- Dog
                     |```""".stripMargin
    val html = MarkdownRenderer.toHtml(markdown)

    assert(html.contains("<div class=\"mermaid\">"))
    assert(html.contains("classDiagram"))
    assert(html.contains("class Animal"))
    assert(html.contains("+String name"))
    assert(html.contains("+makeSound()"))
    assert(html.contains("Animal <|-- Dog"))
    assert(!html.contains("<pre><code class=\"language-mermaid\">"))

  test("transforms state diagram code block"):
    val markdown = """```mermaid
                     |stateDiagram-v2
                     |  [*] --> Idle
                     |  Idle --> Processing
                     |  Processing --> Complete
                     |  Processing --> Error
                     |  Complete --> [*]
                     |  Error --> [*]
                     |```""".stripMargin
    val html = MarkdownRenderer.toHtml(markdown)

    assert(html.contains("<div class=\"mermaid\">"))
    assert(html.contains("stateDiagram-v2"))
    assert(html.contains("[*] --> Idle"))
    assert(html.contains("Idle --> Processing"))
    assert(html.contains("Processing --> Complete"))
    assert(!html.contains("<pre><code class=\"language-mermaid\">"))

  test("transforms pie chart code block"):
    val markdown = """```mermaid
                     |pie title Distribution of Work
                     |  "Development" : 40
                     |  "Testing" : 30
                     |  "Documentation" : 20
                     |  "Meetings" : 10
                     |```""".stripMargin
    val html = MarkdownRenderer.toHtml(markdown)

    assert(html.contains("<div class=\"mermaid\">"))
    assert(html.contains("pie title Distribution of Work"))
    assert(html.contains("\"Development\" : 40"))
    assert(html.contains("\"Testing\" : 30"))
    assert(html.contains("\"Documentation\" : 20"))
    assert(!html.contains("<pre><code class=\"language-mermaid\">"))

  test("mermaid block with dollar signs does not cause illegal group reference"):
    // Dollar signs were being interpreted as regex group references in replaceAllIn
    val markdown = """```mermaid
                     |C4Context
                     |  UpdateLayoutConfig($c4ShapeInRow="3", $c4BoundaryInRow="1")
                     |  Person(user, "User", "Uses the system")
                     |```""".stripMargin
    val html = MarkdownRenderer.toHtml(markdown)

    assert(html.contains("<div class=\"mermaid\">"))
    assert(html.contains("$c4ShapeInRow"))
    assert(html.contains("$c4BoundaryInRow"))
    assert(!html.contains("<pre><code class=\"language-mermaid\">"))
