// PURPOSE: Shared HTML page layout component for dashboard pages
// PURPOSE: Renders complete HTML shell with DOCTYPE, head, CSS/JS links, and body wrapper

package iw.core.dashboard.presentation.views

import scalatags.Text.all.*
import scalatags.Text.tags2.title as titleTag

object PageLayout:
  /** Render complete HTML page with shared layout
    *
    * @param title Page title for the <title> tag
    * @param bodyContent Scalatags fragment to insert in body
    * @param devMode Whether to show dev mode banner
    * @return Complete HTML document as string with DOCTYPE
    */
  def render(title: String, bodyContent: Frag, devMode: Boolean): String =
    val doctype = "<!DOCTYPE html>"
    val htmlContent = html(
      head(
        meta(charset := "UTF-8"),
        meta(name := "viewport", content := "width=device-width, initial-scale=1.0"),
        titleTag(title),
        // HTMX core library
        tag("script")(
          src := "https://unpkg.com/htmx.org@1.9.10",
          attr("integrity") := "sha384-D1Kt99CQMDuVetoL1lrYwg5t+9QdHe7NLX/SoJYkXDFfX37iInKRy5xLSi8nO7UC",
          attr("crossorigin") := "anonymous"
        ),
        // HTMX response-targets extension
        tag("script")(
          src := "https://unpkg.com/htmx-ext-response-targets@2.0.0/response-targets.js"
        ),
        // Dashboard CSS
        link(rel := "stylesheet", href := "/static/dashboard.css"),
        // Dashboard JS
        tag("script")(src := "/static/dashboard.js")
      ),
      body(
        attr("hx-ext") := "response-targets",
        div(
          cls := "container",
          // Dev mode banner (conditional)
          if devMode then
            div(cls := "dev-mode-banner", "DEV MODE")
          else
            (),
          // Page content
          bodyContent
        )
      )
    )

    doctype + htmlContent.render
