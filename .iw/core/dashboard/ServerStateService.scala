// PURPOSE: Centralized service for managing server state with thread-safe operations
// PURPOSE: Holds state in memory, synchronizes writes, and provides per-entry update API

package iw.core.dashboard

import iw.core.model.{ServerState, WorktreeRegistration, CachedIssue, CachedProgress, CachedPR, CachedReviewState}
import iw.core.dashboard.StateRepository
import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.atomic.AtomicReference

class ServerStateService(repository: StateRepository):
  private val stateRef: AtomicReference[ServerState] = new AtomicReference(ServerState(Map.empty, Map.empty, Map.empty, Map.empty, Map.empty))
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
        stateRef.set(loadedState)
        Right(())
      case Left(error) =>
        Left(error)

  /** Get current state (thread-safe read via AtomicReference).
    *
    * @return Current server state
    */
  def getState: ServerState = stateRef.get()

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
      val current = stateRef.get()
      val currentWorktree = current.worktrees.get(issueId)
      val updated = f(currentWorktree) match
        case Some(worktree) =>
          current.copy(worktrees = current.worktrees + (issueId -> worktree))
        case None =>
          current.copy(worktrees = current.worktrees - issueId)
      stateRef.set(updated)
      repository.write(updated) // Best-effort persistence
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
      val current = stateRef.get()
      val currentCache = current.issueCache.get(issueId)
      val updated = f(currentCache) match
        case Some(cached) =>
          current.copy(issueCache = current.issueCache + (issueId -> cached))
        case None =>
          current.copy(issueCache = current.issueCache - issueId)
      stateRef.set(updated)
      repository.write(updated) // Best-effort persistence
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
      val current = stateRef.get()
      val currentCache = current.progressCache.get(issueId)
      val updated = f(currentCache) match
        case Some(cached) =>
          current.copy(progressCache = current.progressCache + (issueId -> cached))
        case None =>
          current.copy(progressCache = current.progressCache - issueId)
      stateRef.set(updated)
      repository.write(updated) // Best-effort persistence
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
      val current = stateRef.get()
      val currentCache = current.prCache.get(issueId)
      val updated = f(currentCache) match
        case Some(cached) =>
          current.copy(prCache = current.prCache + (issueId -> cached))
        case None =>
          current.copy(prCache = current.prCache - issueId)
      stateRef.set(updated)
      repository.write(updated) // Best-effort persistence
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
      val current = stateRef.get()
      val currentCache = current.reviewStateCache.get(issueId)
      val updated = f(currentCache) match
        case Some(cached) =>
          current.copy(reviewStateCache = current.reviewStateCache + (issueId -> cached))
        case None =>
          current.copy(reviewStateCache = current.reviewStateCache - issueId)
      stateRef.set(updated)
      repository.write(updated) // Best-effort persistence
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
      val current = stateRef.get()
      val (valid, invalid) = current.worktrees.partition { case (_, wt) => isValid(wt) }
      val prunedIds = invalid.keySet

      if prunedIds.nonEmpty then
        val updated = current.copy(
          worktrees = valid,
          issueCache = current.issueCache -- prunedIds,
          progressCache = current.progressCache -- prunedIds,
          prCache = current.prCache -- prunedIds,
          reviewStateCache = current.reviewStateCache -- prunedIds
        )
        stateRef.set(updated)
        repository.write(updated) // Best-effort persistence

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

  /** Legacy compatibility: list worktrees by issue ID.
    *
    * @deprecated Use state.listByIssueId directly
    */
  def listWorktrees(state: ServerState): List[WorktreeRegistration] =
    state.listByIssueId
