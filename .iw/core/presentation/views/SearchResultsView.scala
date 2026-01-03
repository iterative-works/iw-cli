// PURPOSE: Presentation layer for rendering issue search results
// PURPOSE: Generates HTML for search result items and empty state message

package iw.core.presentation.views

import scalatags.Text.all.*
import iw.core.domain.IssueSearchResult

object SearchResultsView:
  /** Maximum number of results to display. */
  private val MaxResults = 10

  /** Render search results list.
    *
    * Shows either:
    * - Empty state message if no results
    * - List of result items (max 10)
    *
    * @param results List of search results
    * @return HTML fragment for results
    */
  def render(results: List[IssueSearchResult]): Frag =
    if results.isEmpty then
      renderEmptyState()
    else
      renderResults(results.take(MaxResults))

  /** Render empty state when no results found.
    *
    * @return HTML fragment for empty state
    */
  private def renderEmptyState(): Frag =
    div(
      cls := "search-empty-state",
      p("No issues found")
    )

  /** Render list of search results.
    *
    * @param results List of results (already limited to max)
    * @return HTML fragment for results list
    */
  private def renderResults(results: List[IssueSearchResult]): Frag =
    div(
      results.map(renderResultItem)
    )

  /** Render a single search result item.
    *
    * Each item shows:
    * - Issue ID
    * - Issue title
    * - Issue status
    *
    * When clicked, triggers worktree creation via HTMX POST to /api/worktrees/create.
    *
    * @param result Search result
    * @return HTML fragment for result item
    */
  private def renderResultItem(result: IssueSearchResult): Frag =
    div(
      cls := "search-result-item",
      attr("hx-post") := "/api/worktrees/create",
      attr("hx-vals") := s"""{"issueId": "${result.id}"}""",
      attr("hx-target") := "#modal-body-content",
      attr("hx-swap") := "innerHTML",
      attr("hx-indicator") := "#creation-spinner",
      div(
        cls := "search-result-id",
        result.id
      ),
      div(
        cls := "search-result-title",
        result.title
      ),
      div(
        cls := "search-result-status",
        result.status
      )
    )
