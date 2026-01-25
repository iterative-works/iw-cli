// PURPOSE: Centralized service for managing server state with thread-safe operations
// PURPOSE: Holds state in memory, synchronizes writes, and provides per-entry update API

package iw.core.dashboard

import iw.core.model.{ServerState, WorktreeRegistration, CachedIssue, CachedProgress, CachedPR, CachedReviewState}
import iw.core.dashboard.StateRepository
import java.util.concurrent.locks.ReentrantLock

class ServerStateService(repository: StateRepository):
  @volatile private var state: ServerState = ServerState(Map.empty, Map.empty, Map.empty, Map.empty, Map.empty)
  private val lock = new ReentrantLock()

  /** Initialize service by loading state from repository.
    *
    * Should be called once at startup.
    *
    * @return Right(()) on success, Left(error) on failure
    */
  def initialize(): Either[String, Unit] =
    repository.read() match
      case Right(loadedState) =>
        state = loadedState
        Right(())
      case Left(error) =>
        Left(error)

  /** Get current state (thread-safe read, no lock needed due to @volatile).
    *
    * @return Current server state
    */
  def getState: ServerState = state

  /** Update a worktree entry using a function.
    *
    * The function receives the current worktree (if any) and returns:
    * - Some(worktree) to add/update
    * - None to remove
    *
    * Changes are persisted to disk.
    *
    * @param issueId Issue ID for the worktree
    * @param f Update function
    */
  def updateWorktree(issueId: String)(f: Option[WorktreeRegistration] => Option[WorktreeRegistration]): Unit =
    lock.lock()
    try
      val currentWorktree = state.worktrees.get(issueId)
      f(currentWorktree) match
        case Some(worktree) =>
          state = state.copy(worktrees = state.worktrees + (issueId -> worktree))
        case None =>
          state = state.copy(worktrees = state.worktrees - issueId)
      repository.write(state) // Best-effort persistence
    finally
      lock.unlock()

  /** Update an issue cache entry using a function.
    *
    * @param issueId Issue ID
    * @param f Update function (None to remove, Some to add/update)
    */
  def updateIssueCache(issueId: String)(f: Option[CachedIssue] => Option[CachedIssue]): Unit =
    lock.lock()
    try
      val current = state.issueCache.get(issueId)
      f(current) match
        case Some(cached) =>
          state = state.copy(issueCache = state.issueCache + (issueId -> cached))
        case None =>
          state = state.copy(issueCache = state.issueCache - issueId)
      repository.write(state) // Best-effort persistence
    finally
      lock.unlock()

  /** Update a progress cache entry using a function.
    *
    * @param issueId Issue ID
    * @param f Update function (None to remove, Some to add/update)
    */
  def updateProgressCache(issueId: String)(f: Option[CachedProgress] => Option[CachedProgress]): Unit =
    lock.lock()
    try
      val current = state.progressCache.get(issueId)
      f(current) match
        case Some(cached) =>
          state = state.copy(progressCache = state.progressCache + (issueId -> cached))
        case None =>
          state = state.copy(progressCache = state.progressCache - issueId)
      repository.write(state) // Best-effort persistence
    finally
      lock.unlock()

  /** Update a PR cache entry using a function.
    *
    * @param issueId Issue ID
    * @param f Update function (None to remove, Some to add/update)
    */
  def updatePRCache(issueId: String)(f: Option[CachedPR] => Option[CachedPR]): Unit =
    lock.lock()
    try
      val current = state.prCache.get(issueId)
      f(current) match
        case Some(cached) =>
          state = state.copy(prCache = state.prCache + (issueId -> cached))
        case None =>
          state = state.copy(prCache = state.prCache - issueId)
      repository.write(state) // Best-effort persistence
    finally
      lock.unlock()

  /** Update a review state cache entry using a function.
    *
    * @param issueId Issue ID
    * @param f Update function (None to remove, Some to add/update)
    */
  def updateReviewStateCache(issueId: String)(f: Option[CachedReviewState] => Option[CachedReviewState]): Unit =
    lock.lock()
    try
      val current = state.reviewStateCache.get(issueId)
      f(current) match
        case Some(cached) =>
          state = state.copy(reviewStateCache = state.reviewStateCache + (issueId -> cached))
        case None =>
          state = state.copy(reviewStateCache = state.reviewStateCache - issueId)
      repository.write(state) // Best-effort persistence
    finally
      lock.unlock()

  /** Prune worktrees that don't pass validation.
    *
    * Removes all caches associated with pruned worktrees.
    *
    * @param isValid Function to validate a worktree
    * @return Set of pruned issue IDs
    */
  def pruneWorktrees(isValid: WorktreeRegistration => Boolean): Set[String] =
    lock.lock()
    try
      val (valid, invalid) = state.worktrees.partition { case (_, wt) => isValid(wt) }
      val prunedIds = invalid.keySet

      if prunedIds.nonEmpty then
        state = state.copy(
          worktrees = valid,
          issueCache = state.issueCache -- prunedIds,
          progressCache = state.progressCache -- prunedIds,
          prCache = state.prCache -- prunedIds,
          reviewStateCache = state.reviewStateCache -- prunedIds
        )
        repository.write(state) // Best-effort persistence

      prunedIds
    finally
      lock.unlock()

object ServerStateService:
  /** Legacy compatibility: load state from repository.
    *
    * @deprecated Use ServerStateService instance instead
    */
  def load(repo: StateRepository): Either[String, ServerState] =
    repo.read()

  /** Legacy compatibility: save state to repository.
    *
    * @deprecated Use ServerStateService instance instead
    */
  def save(state: ServerState, repo: StateRepository): Either[String, Unit] =
    repo.write(state)

  /** Legacy compatibility: list worktrees by activity.
    *
    * @deprecated Use state.listByActivity directly
    */
  def listWorktrees(state: ServerState): List[WorktreeRegistration] =
    state.listByActivity
