// PURPOSE: Domain model representing a registered worktree for issue tracking
// PURPOSE: Contains worktree metadata including issue ID, path, tracker type, and activity timestamps

package iw.core.model

import java.time.Instant
import iw.core.model.Issue
import iw.core.model.WorktreeRegistration

case class WorktreeRegistration(
  issueId: String,
  path: String,
  trackerType: String,
  team: String,
  registeredAt: Instant,
  lastSeenAt: Instant
)

object WorktreeRegistration:
  def create(
    issueId: String,
    path: String,
    trackerType: String,
    team: String,
    registeredAt: Instant,
    lastSeenAt: Instant
  ): Either[String, WorktreeRegistration] =
    if issueId.trim.isEmpty then
      Left("Issue ID cannot be empty")
    else if path.trim.isEmpty then
      Left("Path cannot be empty")
    else if trackerType.trim.isEmpty then
      Left("Tracker type cannot be empty")
    else if team.trim.isEmpty then
      Left("Team cannot be empty")
    else
      Right(WorktreeRegistration(issueId, path, trackerType, team, registeredAt, lastSeenAt))
