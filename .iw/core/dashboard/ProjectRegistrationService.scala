// PURPOSE: Pure business logic for project registration operations
// PURPOSE: Handles creating and updating project registrations without I/O

package iw.core.dashboard

import iw.core.model.{ServerState, ProjectRegistration}
import java.time.Instant

object ProjectRegistrationService:

  /**
   * Registers a new project or updates an existing one.
   *
   * For new projects: Sets registeredAt to provided timestamp.
   * For existing projects: Updates projectName/trackerType/team/trackerUrl, preserves registeredAt.
   *
   * @param path The filesystem path to the project
   * @param projectName Display name for the project
   * @param trackerType The tracker system (e.g., "Linear", "GitHub")
   * @param team The team identifier
   * @param trackerUrl Optional URL to the issue tracker
   * @param timestamp The timestamp for this registration
   * @param state The current server state
   * @return Either error message or tuple of (updated server state, wasCreated flag)
   */
  def register(
    path: String,
    projectName: String,
    trackerType: String,
    team: String,
    trackerUrl: Option[String],
    timestamp: Instant,
    state: ServerState
  ): Either[String, (ServerState, Boolean)] =
    ProjectRegistration.create(path, projectName, trackerType, team, trackerUrl, timestamp).map { _ =>
      state.projects.get(path) match
        case Some(existing) =>
          // Update existing: preserve registeredAt, update everything else
          val updated = existing.copy(
            projectName = projectName,
            trackerType = trackerType,
            team = team,
            trackerUrl = trackerUrl
          )
          (state.copy(projects = state.projects + (path -> updated)), false)
        case None =>
          // Create new with provided timestamp as registeredAt
          val created = ProjectRegistration(path, projectName, trackerType, team, trackerUrl, timestamp)
          (state.copy(projects = state.projects + (path -> created)), true)
    }

  /**
   * Removes a project registration from state.
   *
   * Safe to call even if the project is not registered (idempotent).
   *
   * @param path The filesystem path to the project
   * @param state The current server state
   * @return Updated server state with project removed
   */
  def deregister(path: String, state: ServerState): ServerState =
    state.removeProject(path)
