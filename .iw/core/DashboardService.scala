// PURPOSE: Application service for rendering the complete dashboard HTML
// PURPOSE: Generates full HTML page with header, worktree list, and styling

package iw.core.application

import iw.core.domain.WorktreeRegistration
import iw.core.presentation.views.WorktreeListView
import scalatags.Text.all.*

object DashboardService:
  def renderDashboard(worktrees: List[WorktreeRegistration]): String =
    val page = html(
      head(
        meta(charset := "UTF-8"),
        tag("title")("iw Dashboard"),
        tag("style")(raw(styles))
      ),
      body(
        div(
          cls := "container",
          h1("iw Dashboard"),
          WorktreeListView.render(worktrees)
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
    }

    .container {
      max-width: 1200px;
      margin: 0 auto;
    }

    h1 {
      color: #333;
      margin-bottom: 30px;
    }

    .worktree-list {
      display: grid;
      gap: 20px;
      grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
    }

    .worktree-card {
      background: white;
      border: 1px solid #ddd;
      border-radius: 8px;
      padding: 20px;
      box-shadow: 0 2px 4px rgba(0,0,0,0.1);
    }

    .worktree-card h3 {
      margin: 0 0 10px 0;
      color: #0066cc;
      font-size: 18px;
    }

    .worktree-card .title {
      color: #666;
      margin: 0 0 10px 0;
      font-style: italic;
    }

    .worktree-card .last-activity {
      color: #999;
      font-size: 14px;
      margin: 0;
    }

    .empty-state {
      text-align: center;
      padding: 60px 20px;
      color: #999;
    }

    .empty-state p {
      font-size: 18px;
    }
  """
