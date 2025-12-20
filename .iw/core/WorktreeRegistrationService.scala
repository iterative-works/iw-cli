// PURPOSE: Pure business logic for worktree registration operations
// PURPOSE: Handles creating, updating registrations and timestamp management without I/O

package iw.core.service

import iw.core.domain.{ServerState, WorktreeRegistration}
import java.time.Instant

object WorktreeRegistrationService:

  /**
   * Registers a new worktree or updates an existing one.
   *
   * For new worktrees: Sets both registeredAt and lastSeenAt to current time.
   * For existing worktrees: Updates path/trackerType/team and lastSeenAt, preserves registeredAt.
   *
   * @param issueId The issue identifier (e.g., "IWLE-123")
   * @param path The filesystem path to the worktree
   * @param trackerType The tracker system (e.g., "Linear", "YouTrack")
   * @param team The team identifier
   * @param state The current server state
   * @return Either error message or updated server state
   */
  def register(
    issueId: String,
    path: String,
    trackerType: String,
    team: String,
    state: ServerState
  ): Either[String, ServerState] =
    // Validate inputs
    WorktreeRegistration.create(
      issueId,
      path,
      trackerType,
      team,
      Instant.now(), // Temporary value for validation
      Instant.now()  // Temporary value for validation
    ).map { _ =>
      val now = Instant.now()

      // Check if worktree already exists
      val registration = state.worktrees.get(issueId) match
        case Some(existing) =>
          // Update existing: preserve registeredAt, update everything else
          WorktreeRegistration(
            issueId,
            path,
            trackerType,
            team,
            existing.registeredAt,
            now
          )
        case None =>
          // Create new: set both timestamps to now
          WorktreeRegistration(
            issueId,
            path,
            trackerType,
            team,
            now,
            now
          )

      // Return new state with updated worktree
      ServerState(state.worktrees + (issueId -> registration))
    }

  /**
   * Updates only the lastSeenAt timestamp for an existing worktree.
   *
   * @param issueId The issue identifier
   * @param state The current server state
   * @return Either error message or updated server state
   */
  def updateLastSeen(
    issueId: String,
    state: ServerState
  ): Either[String, ServerState] =
    state.worktrees.get(issueId) match
      case Some(existing) =>
        val now = Instant.now()
        val updated = existing.copy(lastSeenAt = now)
        Right(ServerState(state.worktrees + (issueId -> updated)))
      case None =>
        Left(s"Worktree $issueId not found or not registered")
