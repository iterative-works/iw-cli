// PURPOSE: Infrastructure layer for persisting server state to JSON file
// PURPOSE: Handles atomic writes, directory creation, and JSON serialization/deserialization

package iw.core.dashboard

import iw.core.model.{ServerState, ServerStateCodec}
import iw.core.model.ServerStateCodec.{given, *}
import java.nio.file.{Files, Paths, StandardCopyOption}
import scala.util.{Try, Success, Failure}

case class StateRepository(statePath: String):
  import upickle.default.*

  def read(): Either[String, ServerState] =
    val path = Paths.get(statePath)

    if !Files.exists(path) then
      // Create empty state file if it doesn't exist
      ensureDirectoryExists()
      Right(ServerState(Map.empty, Map.empty, Map.empty, Map.empty, Map.empty, Map.empty))
    else
      Try {
        val content = Files.readString(path)
        val stateJson = upickle.default.read[StateJson](content)
        ServerState(stateJson.worktrees, stateJson.issueCache, stateJson.progressCache, stateJson.prCache, stateJson.reviewStateCache, stateJson.projects)
      } match
        case Success(state) => Right(state)
        case Failure(ex) => Left(s"Failed to parse JSON from $statePath: ${ex.getMessage}")

  def write(state: ServerState): Either[String, Unit] =
    Try {
      ensureDirectoryExists()

      val stateJson = StateJson(state.worktrees, state.issueCache, state.progressCache, state.prCache, state.reviewStateCache, state.projects)
      val json = upickle.default.write(stateJson, indent = 2)

      // Atomic write: write to unique temp file, then rename
      val path = Paths.get(statePath)
      val tmpPath = Paths.get(s"$statePath.tmp-${java.util.UUID.randomUUID()}")

      try
        Files.writeString(tmpPath, json)
        Files.move(tmpPath, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
      finally
        // Clean up temp file if it still exists (move failed)
        if Files.exists(tmpPath) then
          Files.delete(tmpPath)
    } match
      case Success(_) => Right(())
      case Failure(ex) => Left(s"Failed to write state to $statePath: ${ex.getMessage}")

  private def ensureDirectoryExists(): Unit =
    val path = Paths.get(statePath)
    val parent = Option(path.getParent)
    parent.foreach { p =>
      if !Files.exists(p) then
        Files.createDirectories(p)
    }
