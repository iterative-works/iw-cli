// PURPOSE: Application service for rendering individual worktree cards with refresh
// PURPOSE: Handles per-card HTML generation with rate limiting and error handling

package iw.core.application

import iw.core.domain.{WorktreeRegistration, IssueData, CachedIssue, WorkflowProgress, CachedProgress, GitStatus, PullRequestData, CachedPR, ReviewState, CachedReviewState}
import iw.core.presentation.views.{WorktreeListView, TimestampFormatter}
import iw.core.{Issue, IssueId}
import scalatags.Text.all.*
import java.time.Instant
import scala.util.Try

object WorktreeCardService:
  /** Render a single worktree card HTML fragment.
    *
    * This method:
    * 1. Checks if refresh is throttled (< 30s since last)
    * 2. If not throttled, attempts to fetch fresh data
    * 3. Falls back to cached data on API failure
    * 4. Returns HTML fragment with HTMX attributes
    *
    * @param issueId Issue ID for the worktree
    * @param worktrees Map of all registered worktrees
    * @param issueCache Current issue cache
    * @param progressCache Current progress cache
    * @param prCache Current PR cache
    * @param reviewStateCache Current review state cache
    * @param throttle Refresh throttle instance
    * @param now Current timestamp
    * @param fetchIssue Function to fetch issue from API
    * @param buildUrl Function to build issue URL
    * @return HTML string for the card, empty string if worktree not found
    */
  def renderCard(
    issueId: String,
    worktrees: Map[String, WorktreeRegistration],
    issueCache: Map[String, CachedIssue],
    progressCache: Map[String, CachedProgress],
    prCache: Map[String, CachedPR],
    reviewStateCache: Map[String, CachedReviewState],
    throttle: RefreshThrottle,
    now: Instant,
    fetchIssue: String => Either[String, Issue],
    buildUrl: (String, String, Option[String]) => String
  ): String =
    worktrees.get(issueId) match
      case None =>
        // Worktree not found
        ""
      case Some(worktree) =>
        // Determine if we should fetch fresh data
        val shouldFetch = throttle.shouldRefresh(issueId, now)

        // Get issue data (cached or fresh)
        val issueDataOpt = if shouldFetch then
          // Try to fetch fresh data
          fetchIssue(issueId) match
            case Right(issue) =>
              throttle.recordRefresh(issueId, now)
              val url = buildUrl(issueId, worktree.trackerType, None)
              val freshData = IssueData.fromIssue(issue, url, now)
              Some((freshData, false, false)) // fresh, not from cache, not stale
            case Left(_) =>
              // API failed, use cached data
              issueCache.get(issueId).map { cached =>
                val isStale = CachedIssue.isStale(cached, now)
                (cached.data, true, isStale)
              }
        else
          // Use cached data (throttled)
          issueCache.get(issueId).map { cached =>
            val isStale = CachedIssue.isStale(cached, now)
            (cached.data, true, isStale)
          }

        // Get other data (progress, git status, PR, review state)
        val progress = progressCache.get(issueId).map(_.progress)
        val gitStatus = None // TODO: Fetch git status if needed
        val prData = prCache.get(issueId).map(_.pr)
        val reviewStateResult = reviewStateCache.get(issueId).map(cached => Right(cached.state))

        // Render the card
        issueDataOpt match
          case Some((data, fromCache, isStale)) =>
            renderCardHtml(worktree, data, fromCache, isStale, progress, gitStatus, prData, reviewStateResult, now)
          case None =>
            renderSkeletonCardHtml(worktree, progress, gitStatus, prData, reviewStateResult, now)

  /** Render normal card with issue data.
    *
    * @param worktree Worktree registration
    * @param data Issue data
    * @param fromCache Whether data is from cache
    * @param isStale Whether cached data is stale
    * @param progress Optional workflow progress
    * @param gitStatus Optional git status
    * @param prData Optional PR data
    * @param reviewStateResult Optional review state
    * @param now Current timestamp
    * @return HTML string
    */
  private def renderCardHtml(
    worktree: WorktreeRegistration,
    data: IssueData,
    fromCache: Boolean,
    isStale: Boolean,
    progress: Option[WorkflowProgress],
    gitStatus: Option[GitStatus],
    prData: Option[PullRequestData],
    reviewStateResult: Option[Either[String, ReviewState]],
    now: Instant
  ): String =
    div(
      cls := "worktree-card",
      id := s"worktree-${worktree.issueId}",
      attr("hx-get") := s"/worktrees/${worktree.issueId}/card",
      attr("hx-trigger") := "load delay:1s, every 30s",
      attr("hx-swap") := "outerHTML",
      // Issue title
      h3(data.title),
      // Issue ID as clickable link
      p(
        cls := "issue-id",
        a(
          href := data.url,
          worktree.issueId
        )
      ),
      // Git status section (if available)
      gitStatus.map { status =>
        div(
          cls := "git-status",
          span(cls := "git-branch", s"Branch: ${status.branchName}"),
          span(
            cls := s"git-indicator ${status.statusCssClass}",
            status.statusIndicator
          )
        )
      },
      // PR link section (if available)
      prData.map { pr =>
        div(
          cls := "pr-link",
          a(
            cls := "pr-button",
            href := pr.url,
            target := "_blank",
            "View PR ↗"
          ),
          span(
            cls := s"pr-badge ${pr.stateBadgeClass}",
            pr.stateBadgeText
          )
        )
      },
      // Phase info and progress bar (if available)
      progress.flatMap(_.currentPhaseInfo).map { phaseInfo =>
        div(
          cls := "phase-info",
          span(
            cls := "phase-label",
            s"Phase ${phaseInfo.phaseNumber}/${progress.get.totalPhases}: ${phaseInfo.phaseName}"
          ),
          div(
            cls := "progress-container",
            div(
              cls := "progress-bar",
              attr("style") := s"width: ${phaseInfo.progressPercentage}%"
            ),
            span(
              cls := "progress-text",
              s"${phaseInfo.completedTasks}/${phaseInfo.totalTasks} tasks"
            )
          )
        )
      },
      // Issue details (status, assignee, cache indicator, stale indicator)
      div(
        cls := "issue-details",
        // Status badge
        span(
          cls := s"status-badge status-${WorktreeListView.statusClass(data.status)}",
          data.status
        ),
        // Assignee (if present)
        data.assignee.map(a =>
          span(cls := "assignee", s" · Assigned: $a")
        ),
        // Cache indicator (if from cache)
        if fromCache then
          span(
            cls := "cache-indicator",
            s" · cached ${WorktreeListView.formatCacheAge(data.fetchedAt, now)}"
          )
        else
          (),
        // Stale indicator (if data is stale)
        if isStale then
          span(
            cls := "stale-indicator",
            s" · stale"
          )
        else
          ()
      ),
      // Review artifacts section (based on review state result)
      reviewStateResult match {
        case None =>
          // No review state file - don't show anything
          ()
        case Some(Left(error)) =>
          // Invalid review state file - show error message
          div(
            cls := "review-artifacts review-error",
            h4("Review Artifacts"),
            p(cls := "review-error-message", "⚠ Review state unavailable"),
            p(cls := "review-error-detail", "The review state file exists but could not be loaded. Check for JSON syntax errors.")
          )
        case Some(Right(state)) if state.artifacts.nonEmpty =>
          // Valid review state with artifacts - show them
          div(
            cls := "review-artifacts",
            // Header with phase number (if available)
            h4(
              "Review Artifacts",
              state.phase.map { phaseNum =>
                span(cls := "review-phase", s" (Phase $phaseNum)")
              }
            ),
            // Status badge (if available)
            state.status.map { statusValue =>
              div(
                cls := s"review-status ${WorktreeListView.statusBadgeClass(statusValue)}",
                span(cls := "review-status-label", WorktreeListView.formatStatusLabel(statusValue))
              )
            },
            // Message (if available)
            state.message.map { msg =>
              p(cls := "review-message", msg)
            },
            // Artifacts list
            ul(
              cls := "artifact-list",
              state.artifacts.map { artifact =>
                li(
                  a(
                    href := s"/worktrees/${worktree.issueId}/artifacts?path=${artifact.path}",
                    artifact.label
                  )
                )
              }
            )
          )
        case Some(Right(state)) =>
          // Valid review state but no artifacts - don't show anything
          ()
      },
      // Update timestamp
      p(
        cls := "update-timestamp",
        TimestampFormatter.formatUpdateTimestamp(data.fetchedAt, now)
      ),
      // Last activity
      p(
        cls := "last-activity",
        s"Last activity: ${WorktreeListView.formatRelativeTime(worktree.lastSeenAt, now)}"
      )
    ).render

  /** Render skeleton card when issue data unavailable.
    *
    * @param worktree Worktree registration
    * @param progress Optional workflow progress
    * @param gitStatus Optional git status
    * @param prData Optional PR data
    * @param reviewStateResult Optional review state
    * @param now Current timestamp
    * @return HTML string
    */
  private def renderSkeletonCardHtml(
    worktree: WorktreeRegistration,
    progress: Option[WorkflowProgress],
    gitStatus: Option[GitStatus],
    prData: Option[PullRequestData],
    reviewStateResult: Option[Either[String, ReviewState]],
    now: Instant
  ): String =
    div(
      cls := "worktree-card skeleton-card",
      id := s"worktree-${worktree.issueId}",
      attr("hx-get") := s"/worktrees/${worktree.issueId}/card",
      attr("hx-trigger") := "load delay:1s, every 30s",
      attr("hx-swap") := "outerHTML",
      // Issue ID as non-clickable placeholder
      h3(cls := "skeleton-title", "Loading..."),
      p(
        cls := "issue-id",
        span(worktree.issueId)
      ),
      // Git status section (if available)
      gitStatus.map { status =>
        div(
          cls := "git-status",
          span(cls := "git-branch", s"Branch: ${status.branchName}"),
          span(
            cls := s"git-indicator ${status.statusCssClass}",
            status.statusIndicator
          )
        )
      },
      // Last activity
      p(
        cls := "last-activity",
        s"Last activity: ${WorktreeListView.formatRelativeTime(worktree.lastSeenAt, now)}"
      )
    ).render
