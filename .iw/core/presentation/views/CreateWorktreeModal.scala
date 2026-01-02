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
    * @return HTML fragment for modal
    */
  def render(): Frag =
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
          h2("Create Worktree"),
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
          // Search input
          input(
            id := "issue-search-input",
            `type` := "text",
            placeholder := "Search by issue ID or title...",
            attr("hx-get") := "/api/issues/search",
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
