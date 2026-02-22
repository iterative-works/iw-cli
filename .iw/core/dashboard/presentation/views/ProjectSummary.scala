// PURPOSE: View model pairing a main project with computed summary statistics
// PURPOSE: Provides pure functions for computing worktree count and attention count per project

package iw.core.dashboard.presentation.views

import iw.core.dashboard.domain.MainProject
import iw.core.model.{CachedReviewState, WorktreeRegistration}

/** View model combining a main project with summary statistics.
  *
  * @param project The main project
  * @param worktreeCount Number of worktrees for this project
  * @param attentionCount Number of worktrees needing attention
  */
case class ProjectSummary(
  project: MainProject,
  worktreeCount: Int,
  attentionCount: Int
)

object ProjectSummary:
  /** Compute project summaries from worktrees and review state cache.
    *
    * Groups worktrees by their derived main project path and computes:
    * - Total worktree count per project
    * - Number of worktrees needing attention (needsAttention == Some(true))
    *
    * @param worktrees List of registered worktrees
    * @param projects List of main projects
    * @param reviewStateCache Map of issue ID to cached review state
    * @return List of project summaries
    */
  def computeSummaries(
    worktrees: List[WorktreeRegistration],
    projects: List[MainProject],
    reviewStateCache: Map[String, CachedReviewState]
  ): List[ProjectSummary] =
    // Group worktrees by their derived main project path
    val worktreesByProject = worktrees.groupBy { wt =>
      MainProject.deriveMainProjectPath(wt.path)
    }

    // Compute summary for each project
    projects.map { project =>
      val projectPath = project.path.toString
      val projectWorktrees = worktreesByProject.getOrElse(Some(projectPath), List.empty)

      val worktreeCount = projectWorktrees.length

      val attentionCount = projectWorktrees.count { wt =>
        reviewStateCache.get(wt.issueId).exists(_.state.needsAttention == Some(true))
      }

      ProjectSummary(project, worktreeCount, attentionCount)
    }
