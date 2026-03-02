// PURPOSE: Domain model representing a registered project for issue tracking
// PURPOSE: Contains project metadata including display name, tracker type, team, and registration timestamp

package iw.core.model

import java.time.Instant

case class ProjectRegistration(
  path: String,
  projectName: String,
  trackerType: String,
  team: String,
  trackerUrl: Option[String],
  registeredAt: Instant
)

object ProjectRegistration:
  def create(
    path: String,
    projectName: String,
    trackerType: String,
    team: String,
    trackerUrl: Option[String],
    registeredAt: Instant
  ): Either[String, ProjectRegistration] =
    if path.trim.isEmpty then
      Left("Path cannot be empty")
    else if projectName.trim.isEmpty then
      Left("Project name cannot be empty")
    else if trackerType.trim.isEmpty then
      Left("Tracker type cannot be empty")
    else if team.trim.isEmpty then
      Left("Team cannot be empty")
    else
      Right(ProjectRegistration(path, projectName, trackerType, team, trackerUrl, registeredAt))
