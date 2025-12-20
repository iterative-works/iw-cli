// PURPOSE: Infrastructure layer for persisting server state to JSON file
// PURPOSE: Handles atomic writes, directory creation, and JSON serialization/deserialization

package iw.core.infrastructure

import iw.core.domain.{ServerState, WorktreeRegistration}
import java.nio.file.{Files, Paths, StandardCopyOption}
import java.time.Instant
import scala.util.{Try, Success, Failure}

case class StateRepository(statePath: String):
  import upickle.default.*

  // JSON serialization for WorktreeRegistration
  given ReadWriter[Instant] = readwriter[String].bimap[Instant](
    instant => instant.toString,
    str => Instant.parse(str)
  )

  given ReadWriter[WorktreeRegistration] = macroRW[WorktreeRegistration]

  // JSON format matching the spec:
  // { "worktrees": { "IWLE-123": { ... }, "IWLE-456": { ... } } }
  case class StateJson(worktrees: Map[String, WorktreeRegistration])
  given ReadWriter[StateJson] = macroRW[StateJson]

  def read(): Either[String, ServerState] =
    val path = Paths.get(statePath)

    if !Files.exists(path) then
      // Create empty state file if it doesn't exist
      ensureDirectoryExists()
      Right(ServerState(Map.empty))
    else
      Try {
        val content = Files.readString(path)
        val stateJson = upickle.default.read[StateJson](content)
        ServerState(stateJson.worktrees)
      } match
        case Success(state) => Right(state)
        case Failure(ex) => Left(s"Failed to parse JSON from $statePath: ${ex.getMessage}")

  def write(state: ServerState): Either[String, Unit] =
    Try {
      ensureDirectoryExists()

      val stateJson = StateJson(state.worktrees)
      val json = upickle.default.write(stateJson, indent = 2)

      // Atomic write: write to temp file, then rename
      val path = Paths.get(statePath)
      val tmpPath = Paths.get(statePath + ".tmp")

      Files.writeString(tmpPath, json)
      Files.move(tmpPath, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    } match
      case Success(_) => Right(())
      case Failure(ex) => Left(s"Failed to write state to $statePath: ${ex.getMessage}")

  private def ensureDirectoryExists(): Unit =
    val path = Paths.get(statePath)
    val parent = path.getParent
    if parent != null && !Files.exists(parent) then
      Files.createDirectories(parent)
