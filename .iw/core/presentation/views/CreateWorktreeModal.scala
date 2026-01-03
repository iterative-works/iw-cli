// PURPOSE: Presentation layer for rendering create worktree modal dialog
// PURPOSE: Generates modal HTML with search input and HTMX integration for issue search

package iw.core.presentation.views

import scalatags.Text.all.*

object CreateWorktreeModal:
  /** Render create worktree modal with search input.
    *
    * The modal includes:
    * - Backdrop overlay
    * - Header with title and close button
    * - Search input with HTMX debounced search
    * - Results container for search results
    *
    * @param projectPath Optional project path to scope the modal to a specific project
    * @return HTML fragment for modal
    */
  def render(projectPath: Option[String] = None): Frag =
    div(
      id := "create-worktree-modal",
      cls := "modal",
      // Backdrop
      div(cls := "modal-backdrop"),
      // Modal content
      div(
        cls := "modal-content",
        // Header
        div(
          cls := "modal-header",
          h2(
            "Create Worktree",
            projectPath.map { path =>
              // Extract project name from path (last component)
              val projectName = path.split('/').lastOption.getOrElse(path)
              span(cls := "modal-project-name", s" - $projectName")
            }
          ),
          button(
            cls := "modal-close",
            attr("hx-get") := "/",
            attr("hx-target") := "#modal-container",
            attr("hx-swap") := "innerHTML",
            "×"
          )
        ),
        // Body
        div(
          cls := "modal-body",
          // Loading indicator (hidden by default, shown by HTMX during requests)
          CreationLoadingView.render(),
          // Modal body content (for HTMX content swapping)
          div(
            id := "modal-body-content",
            // Search input
            input(
              id := "issue-search-input",
              `type` := "text",
              placeholder := "Search by issue ID or title...",
              attr("hx-get") := projectPath.fold("/api/issues/search") { projPath =>
                val encodedPath = java.net.URLEncoder.encode(projPath, "UTF-8")
                s"/api/issues/search?project=$encodedPath"
              },
              attr("hx-trigger") := "keyup changed delay:300ms",
              attr("hx-target") := "#search-results",
              name := "q"
            ),
            // Search results container
            div(
              id := "search-results",
              cls := "search-results"
            )
          )
        )
      )
    )
