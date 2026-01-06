// PURPOSE: Convert markdown content to HTML using flexmark library
// PURPOSE: Provides GitHub Flavored Markdown support with tables, code blocks, and extensions

package iw.core.infrastructure

import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.data.MutableDataSet

object MarkdownRenderer:
  /** Convert markdown content to HTML.
    *
    * Uses flexmark with common extensions enabled:
    * - Tables (GFM style)
    * - Fenced code blocks with syntax highlighting classes
    * - Strikethrough
    * - Autolinks
    *
    * @param markdown Raw markdown content
    * @return Rendered HTML (fragment, not full page)
    */
  def toHtml(markdown: String): String =
    // Configure flexmark with common extensions
    val options = MutableDataSet()

    // Enable GitHub Flavored Markdown features
    options.set(Parser.EXTENSIONS, java.util.Arrays.asList(
      com.vladsch.flexmark.ext.tables.TablesExtension.create(),
      com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension.create(),
      com.vladsch.flexmark.ext.autolink.AutolinkExtension.create(),
      com.vladsch.flexmark.ext.anchorlink.AnchorLinkExtension.create()
    ))

    // Create parser and renderer
    val parser = Parser.builder(options).build()
    val renderer = HtmlRenderer.builder(options).build()

    // Parse and render
    val document = parser.parse(markdown)
    val html = renderer.render(document)

    // Post-process to transform Mermaid code blocks
    transformMermaidBlocks(html)

  /** Transform Mermaid code blocks from flexmark format to Mermaid.js format.
    *
    * Converts:
    *   <pre><code class="language-mermaid">content</code></pre>
    * To:
    *   <div class="mermaid">content</div>
    *
    * HTML entities in the content are decoded (e.g., &gt; becomes >).
    *
    * @param html HTML output from flexmark
    * @return HTML with Mermaid blocks transformed
    */
  private def transformMermaidBlocks(html: String): String =
    // Use DOTALL flag to match across newlines
    val mermaidPattern = """(?s)<pre><code class="language-mermaid">(.*?)</code></pre>""".r

    mermaidPattern.replaceAllIn(html, m => {
      val encodedContent = m.group(1)
      val decodedContent = decodeHtmlEntities(encodedContent)
      // Escape $ characters to prevent them being interpreted as group references
      scala.util.matching.Regex.quoteReplacement(s"""<div class="mermaid">$decodedContent</div>""")
    })

  /** Decode common HTML entities.
    *
    * @param text Text with HTML entities
    * @return Text with entities decoded
    */
  private def decodeHtmlEntities(text: String): String =
    text
      .replace("&lt;", "<")
      .replace("&gt;", ">")
      .replace("&amp;", "&")
      .replace("&quot;", "\"")
      .replace("&#39;", "'")
