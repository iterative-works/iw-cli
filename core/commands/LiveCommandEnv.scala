// PURPOSE: Production CommandEnv implementation wiring capability traits to
// PURPOSE: the existing live adapters (GitAdapter, ReviewStateAdapter, os.*)

package iw.core.commands

import iw.core.adapters.{
  GitAdapter,
  ProcessAdapter,
  ProcessResult,
  ReviewStateAdapter
}
import iw.core.model.{GitRemote, ReviewStateUpdater, StagingCheck}

object LiveConsole extends Console:
  def out(line: String): Unit = System.out.println(line)
  def err(line: String): Unit = System.err.println(line)

object LiveFileSystem extends FileSystem:
  def exists(path: os.Path): Boolean = os.exists(path)
  def read(path: os.Path): Either[String, String] =
    try
      if !os.exists(path) then Left(s"File not found: $path")
      else Right(os.read(path))
    catch case e: Exception => Left(s"Failed to read $path: ${e.getMessage}")
  def write(path: os.Path, content: String): Either[String, Unit] =
    try
      os.write.over(path, content)
      Right(())
    catch case e: Exception => Left(s"Failed to write $path: ${e.getMessage}")

object LiveGitOps extends GitOps:
  def getCurrentBranch(dir: os.Path): Either[String, String] =
    GitAdapter.getCurrentBranch(dir)
  def getFullHeadSha(dir: os.Path): Either[String, String] =
    GitAdapter.getFullHeadSha(dir)
  def push(
      branch: String,
      dir: os.Path,
      setUpstream: Boolean
  ): Either[String, Unit] =
    GitAdapter.push(branch, dir, setUpstream)
  def createAndCheckoutBranch(
      name: String,
      dir: os.Path
  ): Either[String, Unit] =
    GitAdapter.createAndCheckoutBranch(name, dir)
  def commitFileWithRetry(
      path: os.Path,
      message: String,
      dir: os.Path
  ): Either[String, String] =
    GitAdapter.commitFileWithRetry(path, message, dir)
  def getStagingCheck(dir: os.Path): Either[String, StagingCheck] =
    GitAdapter.getStagingCheck(dir)
  def stageFiles(paths: Seq[os.Path], dir: os.Path): Either[String, Unit] =
    GitAdapter.stageFiles(paths, dir)
  def commit(message: String, dir: os.Path): Either[String, String] =
    GitAdapter.commit(message, dir)
  def diffNameOnly(
      baseline: String,
      dir: os.Path
  ): Either[String, List[String]] =
    GitAdapter.diffNameOnly(baseline, dir)
  def checkoutBranch(name: String, dir: os.Path): Either[String, Unit] =
    GitAdapter.checkoutBranch(name, dir)
  def fetchAndReset(branch: String, dir: os.Path): Either[String, Unit] =
    GitAdapter.fetchAndReset(branch, dir)
  def getRemoteUrl(dir: os.Path): Option[GitRemote] =
    GitAdapter.getRemoteUrl(dir)

object LiveReviewStateOps extends ReviewStateOps:
  def update(
      path: os.Path,
      input: ReviewStateUpdater.UpdateInput
  ): Either[String, Unit] =
    ReviewStateAdapter.update(path, input)

object LiveProcess extends Process:
  def commandExists(command: String): Boolean =
    ProcessAdapter.commandExists(command)
  def run(command: Seq[String]): ProcessResult =
    ProcessAdapter.run(command)

final case class LiveCommandEnv(cwd: os.Path) extends CommandEnv:
  val console: Console = LiveConsole
  val fs: FileSystem = LiveFileSystem
  val git: GitOps = LiveGitOps
  val reviewState: ReviewStateOps = LiveReviewStateOps
  val process: Process = LiveProcess

object LiveCommandEnv:
  def default: LiveCommandEnv = LiveCommandEnv(os.pwd)
