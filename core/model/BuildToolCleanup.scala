// PURPOSE: Pure decision for the built-in build-tool teardown on worktree removal
// PURPOSE: Maps worktree filesystem markers to the ordered list of teardown commands

package iw.core.model

/** One build-tool teardown step.
  *
  * @param tool
  *   executable name checked via `commandExists` before running (e.g. "mill")
  * @param command
  *   full argv to execute in the worktree directory
  * @param description
  *   human-readable label printed before running and reused in warning text
  */
case class BuildToolTeardown(
    tool: String,
    command: Seq[String],
    description: String
)

/** Filesystem-marker probe results for a worktree. Pure inputs to `detect`.
  *
  * @param millDaemon
  *   `out/mill-daemon/` exists
  * @param bloopMarker
  *   `.bloop/` or `.scala-build/` exists
  * @param dockerCompose
  *   `docker-compose.yml` exists
  */
case class BuildToolProbe(
    millDaemon: Boolean,
    bloopMarker: Boolean,
    dockerCompose: Boolean
)

object BuildToolCleanup:
  /** Maps present markers to the teardown steps to run, in fixed order: Mill
    * daemon, then Bloop, then docker compose. Absent markers yield no step.
    */
  def detect(probe: BuildToolProbe): List[BuildToolTeardown] =
    List(
      Option.when(probe.millDaemon)(
        BuildToolTeardown(
          "mill",
          Seq("mill", "--no-server", "shutdown"),
          "Shutting down Mill daemon"
        )
      ),
      Option.when(probe.bloopMarker)(
        BuildToolTeardown(
          "bloop",
          Seq("bloop", "exit"),
          "Stopping Bloop server"
        )
      ),
      Option.when(probe.dockerCompose)(
        BuildToolTeardown(
          "docker",
          Seq("docker", "compose", "down"),
          "Stopping docker compose stack"
        )
      )
    ).flatten
