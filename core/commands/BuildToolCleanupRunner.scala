// PURPOSE: Runs the built-in build-tool teardown during iw rm, best-effort
// PURPOSE: Probes the worktree, executes detected teardowns in its cwd, returns warnings

package iw.core.commands

import iw.core.model.{
  BuildToolCleanup,
  BuildToolProbe,
  BuildToolTeardown,
  CleanupContext
}
import scala.util.control.NonFatal

/** Runs the built-in build-tool teardown steps during `iw rm`.
  *
  * Best-effort: probes the worktree for build-tool markers (via
  * `BuildToolCleanup.detect`), then runs each teardown command in the worktree
  * directory through `CommandEnv.process.runIn`. Never throws and never aborts
  * the removal — a missing tool is skipped, any failure becomes a warning.
  */
object BuildToolCleanupRunner:
  /** Per-tool timeout. A graceful daemon shutdown is near-instant; this bound
    * keeps a wedged tool from blocking `rm` for the default 5-minute timeout.
    */
  private val TeardownTimeoutMs = 60_000

  /** Maximum subprocess-output length echoed into a warning, to avoid relaying
    * a runaway or control-character-laden line to the terminal verbatim.
    */
  private val MaxWarningDetail = 200

  /** Best-effort teardown. Returns warnings (never throws, never aborts rm). */
  def run(ctx: CleanupContext, env: CommandEnv): List[String] =
    val wt = ctx.worktreePath
    val probe = BuildToolProbe(
      millDaemon = env.fs.exists(wt / "out" / "mill-daemon"),
      millWrapper = env.fs.exists(wt / "mill"),
      // Either marker triggers an unconditional `bloop exit` (intentionally
      // broad — Bloop restarts on demand); gated on `bloop` being installed,
      // so scala-cli-only setups without standalone bloop simply skip.
      bloopMarker =
        env.fs.exists(wt / ".bloop") || env.fs.exists(wt / ".scala-build"),
      dockerCompose = env.fs.exists(wt / "docker-compose.yml")
    )
    BuildToolCleanup.detect(probe).flatMap(t => runOne(t, wt, env))

  private def runOne(
      teardown: BuildToolTeardown,
      worktree: os.Path,
      env: CommandEnv
  ): Option[String] =
    if !teardown.tool.forall(env.process.commandExists) then
      None // PATH tool not installed — nothing to tear down
    else
      try
        env.console.out(s"${teardown.description}...")
        val result =
          env.process.runIn(worktree, teardown.command, TeardownTimeoutMs)
        if result.exitCode != 0 || result.timedOut then
          val detail =
            Option
              .when(result.stderr.nonEmpty)(result.stderr)
              .orElse(Option.when(result.stdout.nonEmpty)(result.stdout))
              .flatMap(_.linesIterator.nextOption())
              .map(line => s": ${line.take(MaxWarningDetail)}")
              .getOrElse("")
          Some(s"${teardown.description} failed$detail")
        else None
      catch
        case NonFatal(e) =>
          val msg = Option(e.getMessage).getOrElse(e.getClass.getSimpleName)
          Some(s"${teardown.description} failed: $msg")
