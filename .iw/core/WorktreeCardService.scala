// PURPOSE: Application service for rendering individual worktree cards with refresh
// PURPOSE: Handles per-card HTML generation with rate limiting and error handling

package iw.core.application

import iw.core.domain.{WorktreeRegistration, IssueData, CachedIssue, WorkflowProgress, CachedProgress, GitStatus, PullRequestData, CachedPR, ReviewState, CachedReviewState}
import iw.core.presentation.views.{WorktreeCardRenderer, HtmxCardConfig}
import iw.core.{Issue, IssueId}
import scalatags.Text.all.*
import java.time.Instant
import scala.util.Try

/** Result from rendering a card, including any freshly fetched data.
  *
  * @param html Rendered HTML string
  * @param fetchedIssue Freshly fetched issue data (if any) that should be cached
  * @param fetchedProgress Freshly fetched progress data (if any) that should be cached
  * @param fetchedPR Freshly fetched PR data (if any) that should be cached
  * @param fetchedReviewState Freshly fetched review state (if any) that should be cached
  */
case class CardRenderResult(
  html: String,
  fetchedIssue: Option[CachedIssue] = None,
  fetchedProgress: Option[CachedProgress] = None,
  fetchedPR: Option[CachedPR] = None,
  fetchedReviewState: Option[CachedReviewState] = None
)

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
    * @param sshHost SSH hostname for Zed editor links
    * @param fetchIssue Function to fetch issue from API
    * @param buildUrl Function to build issue URL
    * @return CardRenderResult with HTML string and optional fetched issue data
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
    sshHost: String,
    fetchIssue: String => Either[String, Issue],
    buildUrl: (String, String, Option[String]) => String
  ): CardRenderResult =
    worktrees.get(issueId) match
      case None =>
        // Worktree not found
        CardRenderResult("")
      case Some(worktree) =>
        // Determine if we should fetch fresh data
        val shouldFetch = throttle.shouldRefresh(issueId, now)

        // Get issue data (cached or fresh) and track if we fetched fresh data
        val (issueDataOpt, fetchedCachedIssue) = if shouldFetch then
          // Try to fetch fresh data
          fetchIssue(issueId) match
            case Right(issue) =>
              throttle.recordRefresh(issueId, now)
              val url = buildUrl(issueId, worktree.trackerType, None)
              val freshData = IssueData.fromIssue(issue, url, now)
              val cachedIssue = CachedIssue(freshData)
              (Some((freshData, false, false)), Some(cachedIssue)) // fresh, not from cache, not stale
            case Left(_) =>
              // API failed, use cached data
              val cached = issueCache.get(issueId).map { c =>
                val isStale = CachedIssue.isStale(c, now)
                (c.data, true, isStale)
              }
              (cached, None)
        else
          // Use cached data (throttled)
          val cached = issueCache.get(issueId).map { c =>
            val isStale = CachedIssue.isStale(c, now)
            (c.data, true, isStale)
          }
          (cached, None)

        // Refresh review state from filesystem (cheap, always do it)
        val freshReviewState = fetchReviewStateForWorktree(worktree, reviewStateCache)

        // Get other data (progress, git status, PR, review state)
        val progress = progressCache.get(issueId).map(_.progress)
        val gitStatus = None // TODO: Fetch git status if needed
        val prData = prCache.get(issueId).map(_.pr)

        // Use fresh review state if available, otherwise use cached
        val (reviewStateResult, reviewStateCacheUpdate) = freshReviewState match {
          case Some(Right(cached)) =>
            (Some(Right(cached.state)), Some(cached))
          case Some(Left(error)) =>
            (Some(Left(error)), None)
          case None =>
            val cached = reviewStateCache.get(issueId).map(cached => Right(cached.state))
            (cached, None)
        }

        // Render the card using shared renderer
        val html = issueDataOpt match
          case Some((data, fromCache, isStale)) =>
            WorktreeCardRenderer.renderCard(
              worktree, data, fromCache, isStale, progress, gitStatus, prData,
              reviewStateResult, now, sshHost, HtmxCardConfig.polling
            ).render
          case None =>
            WorktreeCardRenderer.renderSkeletonCard(
              worktree, gitStatus, now, HtmxCardConfig.polling
            ).render

        CardRenderResult(html, fetchedCachedIssue, None, None, reviewStateCacheUpdate)

  /** Fetch review state for a single worktree.
    *
    * Reads review-state.json from the worktree and parses review state.
    * Uses cache with mtime validation.
    *
    * Returns:
    * - None: No review state file (normal case, not an error)
    * - Some(Left(error)): File exists but is invalid (parse error, malformed JSON)
    * - Some(Right(cached)): Valid review state
    *
    * @param wt Worktree registration
    * @param cache Current review state cache
    * @return Option[Either[String, CachedReviewState]]
    */
  private def fetchReviewStateForWorktree(
    wt: WorktreeRegistration,
    cache: Map[String, CachedReviewState]
  ): Option[Either[String, CachedReviewState]] =
    // File I/O wrapper: read file content
    val readFile = (path: String) => Try {
      val source = scala.io.Source.fromFile(path)
      try source.mkString
      finally source.close()
    }.toEither.left.map(_.getMessage)

    // File I/O wrapper: get file modification time
    val getMtime = (path: String) => Try {
      java.nio.file.Files.getLastModifiedTime(
        java.nio.file.Paths.get(path)
      ).toMillis
    }.toEither.left.map(_.getMessage)

    // Call ReviewStateService with injected I/O functions
    val reviewStatePath = s"${wt.path}/project-management/issues/${wt.issueId}/review-state.json"

    ReviewStateService.fetchReviewState(
      wt.issueId,
      wt.path,
      cache,
      readFile,
      getMtime
    ) match {
      case Left(err) if err == reviewStatePath || err.contains("NoSuchFileException") || err.contains("File not found") =>
        // Normal case - no review state file
        None
      case Left(err) =>
        // Invalid state file - log warning and return error
        System.err.println(s"[WARN] Failed to load review state for ${wt.issueId}: $err")
        Some(Left(err))
      case Right(cached) =>
        // Valid state
        Some(Right(cached))
    }
