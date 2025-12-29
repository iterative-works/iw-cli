// PURPOSE: Presentation layer for rendering artifact viewing pages with markdown content
// PURPOSE: Provides full HTML pages for artifact display and error pages with back navigation

package iw.core.presentation.views

import scalatags.Text.all.*

object ArtifactView:
  /** Render artifact viewing page.
    *
    * @param artifactLabel Filename of the artifact
    * @param renderedHtml Markdown rendered as HTML
    * @param issueId Issue ID for back link
    * @return Full HTML page
    */
  def render(artifactLabel: String, renderedHtml: String, issueId: String): String =
    val page = html(
      head(
        meta(charset := "UTF-8"),
        tag("title")(s"$artifactLabel - $issueId"),
        tag("style")(raw(styles)),
        // Mermaid.js for diagram rendering
        tag("script")(src := "https://cdn.jsdelivr.net/npm/mermaid@10.9.4/dist/mermaid.min.js"),
        tag("script")(raw(mermaidInitScript))
      ),
      body(
        div(
          cls := "container",
          div(
            cls := "header",
            a(
              cls := "back-link",
              href := "/",
              "← Back to Dashboard"
            ),
            h1(artifactLabel),
            p(cls := "issue-id", issueId)
          ),
          div(
            cls := "content",
            // Raw HTML from markdown renderer
            // flexmark output is safe (escapes user content)
            raw(renderedHtml)
          )
        )
      )
    )

    "<!DOCTYPE html>\n" + page.render

  private val mermaidInitScript = """
    mermaid.initialize({
      startOnLoad: false,
      theme: 'neutral',
      securityLevel: 'loose'
    });

    // Custom rendering with detailed error messages
    document.addEventListener('DOMContentLoaded', async () => {
      const diagrams = document.querySelectorAll('.mermaid');
      for (const el of diagrams) {
        const code = el.textContent;
        const id = 'mermaid-' + Math.random().toString(36).substr(2, 9);
        try {
          const { svg } = await mermaid.render(id, code);
          el.innerHTML = svg;
        } catch (e) {
          // Clean up any orphan SVG created by failed render
          const orphan = document.getElementById(id);
          if (orphan) orphan.remove();

          // Show detailed error message
          const errorMsg = e.message || 'Unknown error';
          el.innerHTML = `
            <div style="background: #ffebee; border: 2px solid #d32f2f; border-radius: 4px; padding: 16px; text-align: left;">
              <div style="display: flex; align-items: flex-start; gap: 12px;">
                <span style="font-size: 24px;">💣</span>
                <div>
                  <strong style="color: #d32f2f;">Mermaid Syntax Error</strong>
                  <pre style="margin: 8px 0 0 0; font-size: 12px; white-space: pre-wrap; color: #333; background: #fff; padding: 8px; border-radius: 4px; overflow-x: auto;">${errorMsg.replace(/</g, '&lt;').replace(/>/g, '&gt;')}</pre>
                </div>
              </div>
            </div>`;
        }
      }
    });
  """

  /** Render error page for artifact loading failures. */
  def renderError(issueId: String, errorMessage: String): String =
    val page = html(
      head(
        meta(charset := "UTF-8"),
        tag("title")("Artifact Error"),
        tag("style")(raw(styles))
      ),
      body(
        div(
          cls := "container",
          div(
            cls := "header",
            a(cls := "back-link", href := "/", "← Back to Dashboard"),
            h1("Artifact Not Found")
          ),
          div(
            cls := "content",
            p(s"Unable to load artifact: $errorMessage"),
            p(a(href := "/", "Return to dashboard"))
          )
        )
      )
    )
    "<!DOCTYPE html>\n" + page.render

  private val styles = """
    body {
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
      margin: 0;
      padding: 20px;
      background-color: #f5f5f5;
      line-height: 1.6;
    }

    .container {
      max-width: 900px;
      margin: 0 auto;
      background: white;
      padding: 40px;
      border-radius: 8px;
      box-shadow: 0 2px 4px rgba(0,0,0,0.1);
    }

    .header {
      border-bottom: 2px solid #e9ecef;
      padding-bottom: 20px;
      margin-bottom: 30px;
    }

    .back-link {
      display: inline-block;
      color: #0066cc;
      text-decoration: none;
      margin-bottom: 10px;
      font-size: 14px;
    }

    .back-link:hover {
      text-decoration: underline;
    }

    h1 {
      margin: 10px 0;
      color: #333;
    }

    .issue-id {
      color: #666;
      font-size: 14px;
      margin: 5px 0 0 0;
    }

    .content {
      color: #333;
    }

    /* Markdown content styling */
    .content h1, .content h2, .content h3, .content h4, .content h5, .content h6 {
      margin-top: 24px;
      margin-bottom: 16px;
      font-weight: 600;
      line-height: 1.25;
    }

    .content h1 { font-size: 2em; border-bottom: 1px solid #eee; padding-bottom: 0.3em; }
    .content h2 { font-size: 1.5em; border-bottom: 1px solid #eee; padding-bottom: 0.3em; }
    .content h3 { font-size: 1.25em; }
    .content h4 { font-size: 1em; }

    .content p {
      margin-bottom: 16px;
    }

    .content ul, .content ol {
      margin-bottom: 16px;
      padding-left: 2em;
    }

    .content li {
      margin-bottom: 4px;
    }

    .content code {
      background: #f6f8fa;
      padding: 0.2em 0.4em;
      border-radius: 3px;
      font-family: 'Courier New', monospace;
      font-size: 0.9em;
    }

    .content pre {
      background: #f6f8fa;
      padding: 16px;
      border-radius: 6px;
      overflow-x: auto;
      margin-bottom: 16px;
    }

    .content pre code {
      background: none;
      padding: 0;
      font-size: 0.9em;
    }

    .content table {
      border-collapse: collapse;
      width: 100%;
      margin-bottom: 16px;
    }

    .content table th,
    .content table td {
      border: 1px solid #ddd;
      padding: 8px 12px;
      text-align: left;
    }

    .content table th {
      background: #f6f8fa;
      font-weight: 600;
    }

    .content blockquote {
      border-left: 4px solid #ddd;
      margin: 0 0 16px 0;
      padding-left: 16px;
      color: #666;
    }

    .content a {
      color: #0066cc;
      text-decoration: none;
    }

    .content a:hover {
      text-decoration: underline;
    }

    .content hr {
      border: none;
      border-top: 1px solid #ddd;
      margin: 24px 0;
    }

    /* Mermaid diagram styling */
    .mermaid {
      margin: 16px 0;
      text-align: center;
    }

    /* Mermaid error styling */
    .mermaid .error-text,
    .mermaid .error-message {
      fill: #d32f2f !important;
      font-family: 'Source Code Pro', 'Courier New', monospace;
      /* Use large font-size to compensate for SVG viewBox scaling */
      font-size: 64px !important;
    }

    .mermaid .error-icon {
      fill: #d32f2f;
    }

    /* Error container styling */
    .mermaid:has(.error-text),
    .mermaid:has(.error-message) {
      border: 2px solid #d32f2f;
      border-radius: 4px;
      padding: 12px;
      background: #ffebee;
    }

    /* Constrain error SVG to show text properly */
    .mermaid:has(.error-text) svg,
    .mermaid:has(.error-message) svg {
      min-height: 150px;
    }
  """
