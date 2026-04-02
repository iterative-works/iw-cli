// PURPOSE: Formatter for displaying registered projects list
// PURPOSE: Provides format method to create human-readable projects overview output

package iw.core.output

import iw.core.model.ProjectSummary

object ProjectsFormatter:
  def format(projects: List[ProjectSummary]): String =
    if projects.isEmpty then "No projects registered."
    else
      val header = "=== Registered Projects ==="
      val lines = projects.map { p =>
        val worktreeLabel =
          if p.worktreeCount == 1 then "worktree" else "worktrees"
        f"${p.name}%-20s ${p.path}%-40s ${p.trackerType}%-10s ${p.team}%-20s ${p.worktreeCount} $worktreeLabel"
      }

      (header :: "" :: lines).mkString("\n")
