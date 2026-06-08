// PURPOSE: CommandEnv trait + capability sub-traits abstract the I/O dependencies
// PURPOSE: of CLI commands so command logic can run in-VM against in-memory fakes

package iw.core.commands

import iw.core.model.{ReviewStateUpdater, StagingCheck}

/** Result of running a command: exit code only. Stdout/stderr flow through
  * `CommandEnv.console` so tests can introspect them via a fake console.
  */
final case class CommandResult(exitCode: Int)

object CommandResult:
  val ok: CommandResult = CommandResult(0)
  val error: CommandResult = CommandResult(1)

/** Line-buffered console boundary. Live writes to System.out/System.err; the
  * fake accumulates lines so tests can assert against them.
  */
trait Console:
  def out(line: String): Unit
  def err(line: String): Unit

/** Minimal filesystem boundary used by command-level code (existence checks and
  * atomic file read/write). Larger directory traversals stay in adapters.
  */
trait FileSystem:
  def exists(path: os.Path): Boolean
  def read(path: os.Path): Either[String, String]
  def write(path: os.Path, content: String): Either[String, Unit]

/** Git operations used by phase commands. Mirrors a subset of `GitAdapter`. */
trait GitOps:
  def getCurrentBranch(dir: os.Path): Either[String, String]
  def getFullHeadSha(dir: os.Path): Either[String, String]
  def push(
      branch: String,
      dir: os.Path,
      setUpstream: Boolean
  ): Either[String, Unit]
  def createAndCheckoutBranch(
      name: String,
      dir: os.Path
  ): Either[String, Unit]
  def commitFileWithRetry(
      path: os.Path,
      message: String,
      dir: os.Path
  ): Either[String, String]
  def getStagingCheck(dir: os.Path): Either[String, StagingCheck]
  def stageFiles(paths: Seq[os.Path], dir: os.Path): Either[String, Unit]
  def commit(message: String, dir: os.Path): Either[String, String]
  def diffNameOnly(
      baseline: String,
      dir: os.Path
  ): Either[String, List[String]]

/** Review-state read/merge/validate/write boundary. Mirrors
  * `ReviewStateAdapter`.
  */
trait ReviewStateOps:
  def update(
      path: os.Path,
      input: ReviewStateUpdater.UpdateInput
  ): Either[String, Unit]

/** Bundle of all capabilities a command needs.
  *
  * Commands take a `CommandEnv` and return `CommandResult`. Production code
  * constructs `LiveCommandEnv`; tests construct an env composed of in-memory
  * fakes from `core/test/fixtures/`.
  */
trait CommandEnv:
  def cwd: os.Path
  def console: Console
  def fs: FileSystem
  def git: GitOps
  def reviewState: ReviewStateOps
