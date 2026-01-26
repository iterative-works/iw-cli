// PURPOSE: Thread-safe registry for tracking in-progress worktree creation operations
// PURPOSE: Prevents concurrent creation attempts for the same issue using ConcurrentHashMap

package iw.core.dashboard.infrastructure

import iw.core.dashboard.domain.CreationLock
import java.util.concurrent.ConcurrentHashMap
import java.time.{Duration, Instant}
import scala.jdk.CollectionConverters.*

object CreationLockRegistry:
  private val locks = new ConcurrentHashMap[String, CreationLock]()

  /** Try to acquire lock for an issue.
    *
    * @param issueId The issue ID to lock
    * @return true if lock was acquired, false if already locked
    */
  def tryAcquire(issueId: String): Boolean =
    val now = Instant.now()
    val lock = CreationLock(issueId, now)
    // putIfAbsent returns null if key was not present, otherwise returns existing value
    locks.putIfAbsent(issueId, lock) == null

  /** Release lock for an issue.
    *
    * Safe to call even if lock doesn't exist.
    *
    * @param issueId The issue ID to unlock
    */
  def release(issueId: String): Unit =
    locks.remove(issueId)

  /** Check if an issue is currently locked.
    *
    * @param issueId The issue ID to check
    * @return true if locked, false otherwise
    */
  def isLocked(issueId: String): Boolean =
    locks.containsKey(issueId)

  /** Remove locks older than the specified maximum age.
    *
    * This cleanup prevents stale locks from blocking creation forever
    * in case of crashes or unexpected termination.
    *
    * @param maxAge Maximum age for a lock (e.g., Duration.ofSeconds(30))
    */
  def cleanupExpired(maxAge: Duration): Unit =
    val now = Instant.now()
    val cutoff = now.minus(maxAge)

    // Find and remove expired locks
    locks.asScala.foreach { case (issueId, lock) =>
      if lock.startedAt.isBefore(cutoff) then
        locks.remove(issueId)
    }

  /** Clear all locks.
    *
    * Used for testing purposes only.
    */
  def clear(): Unit =
    locks.clear()
