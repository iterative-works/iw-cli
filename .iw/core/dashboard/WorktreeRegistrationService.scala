// PURPOSE: Pure business logic for worktree registration operations
// PURPOSE: Handles creating, updating registrations and timestamp management without I/O

package iw.core.dashboard

import iw.core.model.{ServerState, WorktreeRegistration}
import java.time.Instant

object WorktreeRegistrationService:

  /**
   * Registers a new worktree or updates an existing one.
   *
   * For new worktrees: Sets both registeredAt and lastSeenAt to provided timestamp.
   * For existing worktrees: Updates path/trackerType/team and lastSeenAt, preserves registeredAt.
   *
   * @param issueId The issue identifier (e.g., "IWLE-123")
   * @param path The filesystem path to the worktree
   * @param trackerType The tracker system (e.g., "Linear", "YouTrack")
   * @param team The team identifier
   * @param timestamp The timestamp for this registration
   * @param state The current server state
   * @return Either error message or tuple of (updated server state, wasCreated flag)
   */
  def register(
    issueId: String,
    path: String,
    trackerType: String,
    team: String,
    timestamp: Instant,
    state: ServerState
  ): Either[String, (ServerState, Boolean)] =
    // Validate inputs
    WorktreeRegistration.create(
      issueId,
      path,
      trackerType,
      team,
      timestamp, // Use provided timestamp for validation
      timestamp  // Use provided timestamp for validation
    ).map { _ =>
      // Check if worktree already exists
      val (registration, wasCreated) = state.worktrees.get(issueId) match
        case Some(existing) =>
          // Update existing: preserve registeredAt, update everything else
          val updated = WorktreeRegistration(
            issueId,
            path,
            trackerType,
            team,
            existing.registeredAt,
            timestamp
          )
          (updated, false)
        case None =>
          // Create new: set both timestamps to provided timestamp
          val created = WorktreeRegistration(
            issueId,
            path,
            trackerType,
            team,
            timestamp,
            timestamp
          )
          (created, true)

      // Return new state with updated worktree and creation flag
      (ServerState(state.worktrees + (issueId -> registration)), wasCreated)
    }

  /**
   * Updates only the lastSeenAt timestamp for an existing worktree.
   *
   * @param issueId The issue identifier
   * @param timestamp The timestamp for this update
   * @param state The current server state
   * @return Either error message or updated server state
   */
  def updateLastSeen(
    issueId: String,
    timestamp: Instant,
    state: ServerState
  ): Either[String, ServerState] =
    state.worktrees.get(issueId) match
      case Some(existing) =>
        val updated = existing.copy(lastSeenAt = timestamp)
        Right(ServerState(state.worktrees + (issueId -> updated)))
      case None =>
        Left(s"Worktree $issueId not found or not registered")
