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
    renderer.render(document)
