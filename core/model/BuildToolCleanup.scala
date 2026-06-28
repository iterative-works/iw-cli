// PURPOSE: Pure decision for the built-in build-tool teardown on worktree removal
// PURPOSE: Maps worktree filesystem markers to the ordered list of teardown commands

package iw.core.model

/** One build-tool teardown step.
  *
  * @param tool
  *   executable name checked on PATH via `commandExists` before running (e.g.
  *   "mill"); `None` when the command is a known file already present in the
  *   worktree (e.g. its `./mill` wrapper), which needs no PATH gating
  * @param command
  *   full argv to execute in the worktree directory
  * @param description
  *   human-readable label printed before running and reused in warning text
  */
case class BuildToolTeardown(
    tool: Option[String],
    command: Seq[String],
    description: String
)

/** Filesystem-marker probe results for a worktree. Pure inputs to `detect`.
  *
  * @param millDaemon
  *   `out/mill-daemon/` exists
  * @param millWrapper
  *   a vendored `./mill` wrapper exists in the worktree root
  * @param bloopMarker
  *   `.bloop/` or `.scala-build/` exists
  * @param dockerCompose
  *   `docker-compose.yml` exists
  */
case class BuildToolProbe(
    millDaemon: Boolean,
    millWrapper: Boolean,
    bloopMarker: Boolean,
    dockerCompose: Boolean
)

object BuildToolCleanup:
  /** Maps present markers to the teardown steps to run, in fixed order: Mill
    * daemon, then Bloop, then docker compose. Absent markers yield no step.
    */
  def detect(probe: BuildToolProbe): List[BuildToolTeardown] =
    List(
      Option.when(probe.millDaemon)(millTeardown(probe.millWrapper)),
      Option.when(probe.bloopMarker)(
        BuildToolTeardown(
          Some("bloop"),
          Seq("bloop", "exit"),
          "Stopping Bloop server"
        )
      ),
      Option.when(probe.dockerCompose)(
        BuildToolTeardown(
          Some("docker"),
          Seq("docker", "compose", "down"),
          "Stopping docker compose stack"
        )
      )
    ).flatten

  /** The vendored `./mill` wrapper started the daemon and pins the Mill
    * version, so it is the right executable to shut that daemon down; a PATH
    * `mill` may be a different version or launcher targeting a different
    * daemon. Fall back to a PATH-gated `mill` only when the worktree has no
    * wrapper.
    */
  private def millTeardown(wrapperPresent: Boolean): BuildToolTeardown =
    val (tool, exe) =
      if wrapperPresent then (None, "./mill")
      else (Some("mill"), "mill")
    BuildToolTeardown(
      tool,
      Seq(exe, "--no-server", "shutdown"),
      "Shutting down Mill daemon"
    )
