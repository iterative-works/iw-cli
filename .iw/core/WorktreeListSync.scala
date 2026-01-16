// PURPOSE: Application service for worktree list synchronization and change detection
// PURPOSE: Provides diff logic to detect additions, deletions, and reorders, and generates HTMX OOB swap HTML

package iw.core.application

import iw.core.domain.{WorktreeRegistration, CachedIssue, CachedProgress, CachedPR, CachedReviewState, IssueData, WorkflowProgress, PullRequestData, ReviewState}
import iw.core.presentation.views.{WorktreeListView, TimestampFormatter}
import scalatags.Text.all.*
import java.time.Instant

object WorktreeListSync:
  /** Result of comparing two worktree lists.
    *
    * @param additions Issue IDs that are new in the second list
    * @param deletions Issue IDs that were removed from the first list
    * @param reorders Issue IDs that changed position (moved)
    */
  case class ListChanges(
    additions: List[String],
    deletions: List[String],
    reorders: List[String]
  )

  /** Detect changes between two ordered lists of issue IDs.
    *
    * This is the core diff algorithm for worktree list synchronization.
    *
    * @param oldIds Previous list of issue IDs (ordered)
    * @param newIds Current list of issue IDs (ordered)
    * @return ListChanges describing what changed
    */
  def detectChanges(oldIds: List[String], newIds: List[String]): ListChanges =
    val oldSet = oldIds.toSet
    val newSet = newIds.toSet

    // Find additions and deletions
    val additions = newIds.filterNot(oldSet.contains)
    val deletions = oldIds.filterNot(newSet.contains)

    // Find reorders: items that exist in both lists but changed position
    val commonIds = oldIds.filter(newSet.contains)
    val commonInNewOrder = newIds.filter(oldSet.contains)

    // Check if order changed - compare the sequences
    val reorders = if commonIds != commonInNewOrder then
      // Items that changed position
      val oldPositions = commonIds.zipWithIndex.toMap
      val newPositions = commonInNewOrder.zipWithIndex.toMap

      commonIds.filter { id =>
        oldPositions.get(id) != newPositions.get(id)
      }
    else
      List.empty[String]

    ListChanges(additions, deletions, reorders)

  /** Generate HTMX OOB swap HTML for adding a new worktree card.
    *
    * Returns HTML with `hx-swap-oob="beforeend:#worktree-list"` to append card to list.
    *
    * @param registration Worktree registration
    * @param issueData Optional cached issue data
    * @param progress Optional workflow progress
    * @param prData Optional PR data
    * @param reviewState Optional review state
    * @param now Current timestamp
    * @param sshHost SSH hostname for Zed links
    * @return HTML string with OOB swap attribute
    */
  def generateAdditionOob(
    registration: WorktreeRegistration,
    issueData: Option[CachedIssue],
    progress: Option[CachedProgress],
    prData: Option[CachedPR],
    reviewState: Option[CachedReviewState],
    now: Instant,
    sshHost: String
  ): String =
    // Render card HTML directly
    val issueDataTuple = issueData.map(c => (c.data, true, CachedIssue.isStale(c, now)))
    val cardContent = renderCardContent(
      registration,
      issueDataTuple,
      progress.map(_.progress),
      prData.map(_.pr),
      reviewState.map(c => Right(c.state)),
      now,
      sshHost
    )

    // Return div with OOB attribute and card ID
    div(
      id := s"card-${registration.issueId}",
      attr("hx-swap-oob") := "beforeend:#worktree-list",
      cardContent
    ).render

  /** Generate HTMX OOB swap HTML for deleting a worktree card.
    *
    * Returns minimal HTML with `hx-swap-oob="delete"` to remove card from list.
    *
    * @param issueId Issue ID for the card to delete
    * @return HTML string with OOB delete attribute
    */
  def generateDeletionOob(issueId: String): String =
    div(
      id := s"card-$issueId",
      attr("hx-swap-oob") := "delete"
    ).render

  /** Generate HTMX OOB swap HTML for reordering a worktree card.
    *
    * Returns two HTML elements:
    * 1. Delete the old card
    * 2. Add it at the new position
    *
    * @param registration Worktree registration
    * @param issueData Optional cached issue data
    * @param progress Optional workflow progress
    * @param prData Optional PR data
    * @param reviewState Optional review state
    * @param position New position (0 = top, 1 = second, etc.)
    * @param now Current timestamp
    * @param sshHost SSH hostname for Zed links
    * @return HTML string with delete + add OOB swaps
    */
  def generateReorderOob(
    registration: WorktreeRegistration,
    issueData: Option[CachedIssue],
    progress: Option[CachedProgress],
    prData: Option[CachedPR],
    reviewState: Option[CachedReviewState],
    position: Int,
    now: Instant,
    sshHost: String
  ): String =
    val deleteHtml = generateDeletionOob(registration.issueId)

    // Render card content
    val issueDataTuple = issueData.map(c => (c.data, true, CachedIssue.isStale(c, now)))
    val cardContent = renderCardContent(
      registration,
      issueDataTuple,
      progress.map(_.progress),
      prData.map(_.pr),
      reviewState.map(c => Right(c.state)),
      now,
      sshHost
    )

    // Use afterbegin for position 0 (top), otherwise use beforeend
    val swapStrategy = if position == 0 then "afterbegin:#worktree-list" else "beforeend:#worktree-list"

    val addHtml = div(
      id := s"card-${registration.issueId}",
      attr("hx-swap-oob") := swapStrategy,
      cardContent
    ).render

    deleteHtml + addHtml

  /** Generate complete HTMX response with all OOB swaps for detected changes.
    *
    * Returns empty string if no changes.
    *
    * @param changes Detected list changes
    * @param registrations All worktree registrations (by issue ID)
    * @param issueCache Issue data cache
    * @param progressCache Progress data cache
    * @param prCache PR data cache
    * @param reviewStateCache Review state cache
    * @param now Current timestamp
    * @param sshHost SSH hostname for Zed links
    * @return HTML string with all OOB swaps, or empty string
    */
  def generateChangesResponse(
    changes: ListChanges,
    registrations: Map[String, WorktreeRegistration],
    issueCache: Map[String, CachedIssue],
    progressCache: Map[String, CachedProgress],
    prCache: Map[String, CachedPR],
    reviewStateCache: Map[String, CachedReviewState],
    now: Instant,
    sshHost: String
  ): String =
    if changes.additions.isEmpty && changes.deletions.isEmpty && changes.reorders.isEmpty then
      // No changes - return empty response
      ""
    else
      val additionsHtml = changes.additions.flatMap { issueId =>
        registrations.get(issueId).map { reg =>
          generateAdditionOob(
            reg,
            issueCache.get(issueId),
            progressCache.get(issueId),
            prCache.get(issueId),
            reviewStateCache.get(issueId),
            now,
            sshHost
          )
        }
      }

      val deletionsHtml = changes.deletions.map(generateDeletionOob)

      val reordersHtml = changes.reorders.flatMap { issueId =>
        registrations.get(issueId).map { reg =>
          // Reordered items should go to top (position 0)
          generateReorderOob(
            reg,
            issueCache.get(issueId),
            progressCache.get(issueId),
            prCache.get(issueId),
            reviewStateCache.get(issueId),
            0, // Move to top
            now,
            sshHost
          )
        }
      }

      (deletionsHtml ++ reordersHtml ++ additionsHtml).mkString

  /** Render card content as Scalatags Frag.
    *
    * This renders the inner content of a worktree card for OOB swaps.
    *
    * @param worktree Worktree registration
    * @param issueDataOpt Optional tuple of (IssueData, fromCache, isStale)
    * @param progress Optional workflow progress
    * @param prData Optional PR data
    * @param reviewStateResult Optional review state result
    * @param now Current timestamp
    * @param sshHost SSH hostname for Zed links
    * @return Frag with card content
    */
  private def renderCardContent(
    worktree: WorktreeRegistration,
    issueDataOpt: Option[(IssueData, Boolean, Boolean)],
    progress: Option[WorkflowProgress],
    prData: Option[PullRequestData],
    reviewStateResult: Option[Either[String, ReviewState]],
    now: Instant,
    sshHost: String
  ): Frag =
    issueDataOpt match
      case Some((data, fromCache, isStale)) =>
        div(
          cls := "worktree-card",
          attr("hx-get") := s"/worktrees/${worktree.issueId}/card",
          attr("hx-trigger") := "every 30s",
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
        )
      case None =>
        // Skeleton card
        div(
          cls := "worktree-card skeleton-card",
          attr("hx-get") := s"/worktrees/${worktree.issueId}/card",
          attr("hx-trigger") := "every 30s",
          attr("hx-swap") := "outerHTML",
          // Issue ID as non-clickable placeholder
          h3(cls := "skeleton-title", "Loading..."),
          p(
            cls := "issue-id",
            span(worktree.issueId)
          ),
          // Last activity
          p(
            cls := "last-activity",
            s"Last activity: ${WorktreeListView.formatRelativeTime(worktree.lastSeenAt, now)}"
          )
        )
