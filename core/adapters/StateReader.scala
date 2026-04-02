// PURPOSE: Read-only adapter for server state file access
// PURPOSE: Provides read() method for CLI commands to query worktree and issue state without write capability

package iw.core.adapters

import iw.core.model.{ServerState, ServerStateCodec}
import iw.core.model.ServerStateCodec.*
import java.nio.file.{Files, Paths}
import scala.util.{Try, Success, Failure}

object StateReader:
  /** Default state file location */
  val DefaultStatePath: String =
    s"${sys.env.getOrElse("HOME", "/tmp")}/.local/share/iw/server/state.json"

  /** Read server state from a JSON file. Returns empty state if file is
    * missing.
    *
    * @param statePath
    *   Path to state.json file
    * @return
    *   Right(ServerState) on success, Left(error message) on parse failure
    */
  def read(statePath: String = DefaultStatePath): Either[String, ServerState] =
    val path = Paths.get(statePath)

    if !Files.exists(path) then
      // Return empty state if file doesn't exist (not an error)
      Right(ServerState(Map.empty, Map.empty, Map.empty, Map.empty, Map.empty))
    else
      Try {
        val content = Files.readString(path)
        val stateJson = upickle.default.read[StateJson](content)
        ServerState(
          stateJson.worktrees,
          stateJson.issueCache,
          stateJson.progressCache,
          stateJson.prCache,
          stateJson.reviewStateCache,
          stateJson.projects
        )
      } match
        case Success(state) => Right(state)
        case Failure(ex)    =>
          Left(s"Failed to parse JSON from $statePath: ${ex.getMessage}")
