// PURPOSE: Shared worktree card rendering logic used by all card display contexts
// PURPOSE: Single source of truth for card HTML structure to prevent feature drift

package iw.core.dashboard.presentation.views

import iw.core.model.{WorktreeRegistration, IssueData, WorkflowProgress, GitStatus, PullRequestData, ReviewState, PRState}
import scalatags.Text.all.*
import java.time.Instant
import iw.core.dashboard.WorktreeListView
import iw.core.output.TimestampFormatter

/** Configuration for HTMX attributes on cards.
  *
  * Different contexts need different HTMX behaviors:
  * - Initial dashboard: includes `refresh from:body` trigger and transitions
  * - Card refresh endpoint: simple polling only
  * - OOB additions: simple polling with OOB swap attribute
  *
  * @param trigger hx-trigger attribute value
  * @param swap hx-swap attribute value
  * @param oobSwap Optional hx-swap-oob attribute for OOB responses
  */
case class HtmxCardConfig(
  trigger: String,
  swap: String,
  oobSwap: Option[String] = None
)

object HtmxCardConfig:
  /** Config for initial dashboard render - includes refresh trigger and transitions */
  val dashboard: HtmxCardConfig = HtmxCardConfig(
    trigger = "every 30s, refresh from:body",
    swap = "outerHTML transition:true"
  )

  /** Config for card refresh endpoint - simple polling */
  val polling: HtmxCardConfig = HtmxCardConfig(
    trigger = "every 30s",
    swap = "outerHTML"
  )

  /** Config for OOB addition - appends to worktree list */
  def oobAddition: HtmxCardConfig = HtmxCardConfig(
    trigger = "every 30s",
    swap = "outerHTML",
    oobSwap = Some("beforeend:#worktree-list")
  )

  /** Config for OOB at specific position */
  def oobAtPosition(position: Int): HtmxCardConfig = HtmxCardConfig(
    trigger = "every 30s",
    swap = "outerHTML",
    oobSwap = Some(if position == 0 then "afterbegin:#worktree-list" else "beforeend:#worktree-list")
  )

object WorktreeCardRenderer:

  /** Render a worktree card with all data available.
    *
    * @param worktree Worktree registration
    * @param data Issue data
    * @param fromCache Whether data is from cache
    * @param isStale Whether cached data is stale
    * @param progress Optional workflow progress
    * @param gitStatus Optional git status
    * @param prData Optional PR data
    * @param reviewStateResult Optional review state (Left = error, Right = valid state)
    * @param now Current timestamp
    * @param sshHost SSH hostname for Zed editor links
    * @param htmxConfig HTMX attribute configuration
    * @return Scalatags Frag
    */
  def renderCard(
    worktree: WorktreeRegistration,
    data: IssueData,
    fromCache: Boolean,
    isStale: Boolean,
    progress: Option[WorkflowProgress],
    gitStatus: Option[GitStatus],
    prData: Option[PullRequestData],
    reviewStateResult: Option[Either[String, ReviewState]],
    now: Instant,
    sshHost: String,
    htmxConfig: HtmxCardConfig
  ): Frag =
    div(
      cls := "worktree-card",
      id := s"card-${worktree.issueId}",
      attr("hx-get") := s"/worktrees/${worktree.issueId}/card",
      attr("hx-trigger") := htmxConfig.trigger,
      attr("hx-swap") := htmxConfig.swap,
      attr("hx-disinherit") := "hx-vals",  // Attempt to prevent inheriting parent's hx-vals (HTMX bug #1119 may prevent this)
      htmxConfig.oobSwap.map(oob => attr("hx-swap-oob") := oob),
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
      // Zed editor button
      div(
        cls := "zed-link",
        a(
          cls := "zed-button",
          href := s"zed://ssh/$sshHost${worktree.path}",
          attr("title") := "Open in Zed",
          img(
            src := "https://raw.githubusercontent.com/zed-industries/zed/main/crates/zed/resources/app-icon.png",
            alt := "Zed",
            attr("width") := "18",
            attr("height") := "18"
          )
        )
      ),
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
      renderReviewArtifacts(worktree.issueId, reviewStateResult),
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
    )

  /** Render a skeleton card when issue data is not yet available.
    *
    * @param worktree Worktree registration
    * @param gitStatus Optional git status (may be available before issue data)
    * @param now Current timestamp
    * @param htmxConfig HTMX attribute configuration
    * @return Scalatags Frag
    */
  def renderSkeletonCard(
    worktree: WorktreeRegistration,
    gitStatus: Option[GitStatus],
    now: Instant,
    htmxConfig: HtmxCardConfig
  ): Frag =
    div(
      cls := "worktree-card skeleton-card",
      id := s"card-${worktree.issueId}",
      attr("hx-get") := s"/worktrees/${worktree.issueId}/card",
      attr("hx-trigger") := htmxConfig.trigger,
      attr("hx-swap") := htmxConfig.swap,
      attr("hx-disinherit") := "hx-vals",  // Attempt to prevent inheriting parent's hx-vals (HTMX bug #1119 may prevent this)
      htmxConfig.oobSwap.map(oob => attr("hx-swap-oob") := oob),
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
    )

  /** Render review artifacts section.
    *
    * @param issueId Issue ID for artifact links
    * @param reviewStateResult Optional review state result
    * @return Scalatags Frag
    */
  private def renderReviewArtifacts(
    issueId: String,
    reviewStateResult: Option[Either[String, ReviewState]]
  ): Frag =
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
          h4("Review Artifacts"),
          // Display badge (if available)
          state.display.map { display =>
            div(
              cls := s"review-status ${WorktreeListView.displayTypeClass(display.displayType)}",
              span(cls := "review-status-label", display.text),
              display.subtext.map { subtext =>
                span(cls := "review-status-subtext", s" · $subtext")
              }
            )
          },
          // Additional badges (if available)
          state.badges.map { badgeList =>
            div(
              cls := "review-badges",
              badgeList.map { badge =>
                span(
                  cls := s"review-badge ${WorktreeListView.displayTypeClass(badge.badgeType)}",
                  badge.label
                )
              }
            )
          },
          // Needs attention indicator (if set)
          state.needsAttention.filter(_ == true).map { _ =>
            div(
              cls := "needs-attention-indicator",
              "⚠ Needs Attention"
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
                  href := s"/worktrees/$issueId/artifacts?path=${artifact.path}",
                  artifact.label
                )
              )
            }
          )
        )
      case Some(Right(state)) =>
        // Valid review state but no artifacts - don't show anything
        ()
    }
