// PURPOSE: Application service for worktree list synchronization and change detection
// PURPOSE: Provides diff logic to detect additions, deletions, and reorders, and generates HTMX OOB swap HTML

package iw.core.dashboard

import iw.core.model.{WorktreeRegistration, CachedIssue, CachedProgress, CachedPR, CachedReviewState, IssueData, WorkflowProgress, PullRequestData, ReviewState}
import iw.core.dashboard.presentation.views.{WorktreeCardRenderer, HtmxCardConfig}
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
    // Render card without OOB attribute (using polling config)
    val cardHtml = issueData match
      case Some(cached) =>
        val isStale = CachedIssue.isStale(cached, now)
        WorktreeCardRenderer.renderCard(
          registration,
          cached.data,
          fromCache = true,
          isStale = isStale,
          progress.map(_.progress),
          gitStatus = None,
          prData.map(_.pr),
          reviewState.map(c => Right(c.state)),
          now,
          sshHost,
          HtmxCardConfig.polling
        )
      case None =>
        WorktreeCardRenderer.renderSkeletonCard(
          registration,
          gitStatus = None,
          now,
          HtmxCardConfig.polling
        )

    // Wrap card in OOB carrier div - HTMX uses inner content for beforeend
    div(
      attr("hx-swap-oob") := "beforeend:#worktree-list",
      cardHtml
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

    // Render card without OOB attribute (using polling config)
    val cardHtml = issueData match
      case Some(cached) =>
        val isStale = CachedIssue.isStale(cached, now)
        WorktreeCardRenderer.renderCard(
          registration,
          cached.data,
          fromCache = true,
          isStale = isStale,
          progress.map(_.progress),
          gitStatus = None,
          prData.map(_.pr),
          reviewState.map(c => Right(c.state)),
          now,
          sshHost,
          HtmxCardConfig.polling
        )
      case None =>
        WorktreeCardRenderer.renderSkeletonCard(
          registration,
          gitStatus = None,
          now,
          HtmxCardConfig.polling
        )

    // Wrap card in OOB carrier div with position-based swap
    val oobTarget = if position == 0 then "afterbegin:#worktree-list" else "beforeend:#worktree-list"
    val addHtml = div(
      attr("hx-swap-oob") := oobTarget,
      cardHtml
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
