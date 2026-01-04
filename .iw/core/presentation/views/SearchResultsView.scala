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
    * @param projectPath Optional project path to scope worktree creation
    * @return HTML fragment for results
    */
  def render(results: List[IssueSearchResult], projectPath: Option[String] = None): Frag =
    if results.isEmpty then
      renderEmptyState()
    else
      renderResults(results.take(MaxResults), projectPath)

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
    * @param projectPath Optional project path to scope worktree creation
    * @return HTML fragment for results list
    */
  private def renderResults(results: List[IssueSearchResult], projectPath: Option[String]): Frag =
    div(
      attr("hx-on::before-request") := "this.classList.add('disabled')",
      attr("hx-on::after-request") := "this.classList.remove('disabled')",
      results.map(result => renderResultItem(result, projectPath))
    )

  /** Render a single search result item.
    *
    * Each item shows:
    * - Issue ID
    * - Issue title
    * - Issue status
    * - "Already has worktree" badge (if applicable)
    *
    * When clicked, triggers worktree creation via HTMX POST to /api/worktrees/create,
    * unless the issue already has a worktree.
    *
    * @param result Search result
    * @param projectPath Optional project path to scope worktree creation
    * @return HTML fragment for result item
    */
  private def renderResultItem(result: IssueSearchResult, projectPath: Option[String]): Frag =
    val baseAttrs = Seq(
      cls := "search-result-item"
    )

    val htmxAttrs = if !result.hasWorktree then
      // Build hx-vals JSON with issueId and optional projectPath
      val valsJson = projectPath match
        case Some(path) =>
          s"""{"issueId": "${result.id}", "projectPath": "$path"}"""
        case None =>
          s"""{"issueId": "${result.id}"}"""

      Seq(
        attr("hx-post") := "/api/worktrees/create",
        attr("hx-vals") := valsJson,
        attr("hx-target") := "#modal-body-content",
        attr("hx-swap") := "innerHTML",
        attr("hx-indicator") := "#creation-spinner"
      )
    else
      Seq.empty

    div(
      baseAttrs ++ htmxAttrs,
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
      ),
      if result.hasWorktree then
        renderWorktreeBadge()
      else
        frag()
    )

  /** Render "Already has worktree" badge.
    *
    * @return HTML fragment for badge
    */
  private def renderWorktreeBadge(): Frag =
    span(
      cls := "worktree-badge",
      "Already has worktree"
    )
