// PURPOSE: In-memory fakes implementing CommandEnv capability traits for harness tests
// PURPOSE: FakeConsole captures stdout/stderr; FakeFileSystem + FakeGit + FakeReviewState track state

package iw.core.test.fixtures

import iw.core.commands.{
  CommandEnv,
  Console,
  FileSystem,
  GitOps,
  ReviewStateOps
}
import iw.core.model.{ReviewStateUpdater, ReviewStateValidator, StagingCheck}

import java.util.concurrent.atomic.AtomicReference
import scala.collection.mutable

/** Captures `out` and `err` lines into separate buffers so tests can assert
  * against rendered output without touching real stdout/stderr.
  */
final class FakeConsole extends Console:
  private val outBuf = mutable.ArrayBuffer.empty[String]
  private val errBuf = mutable.ArrayBuffer.empty[String]

  def out(line: String): Unit = outBuf += line
  def err(line: String): Unit = errBuf += line

  def stdout: String = outBuf.mkString("\n")
  def stderr: String = errBuf.mkString("\n")
  def stdoutLines: List[String] = outBuf.toList
  def stderrLines: List[String] = errBuf.toList

/** In-memory file store keyed by absolute path. Tests can pre-populate files
  * and inspect mutations after the command runs.
  */
final class FakeFileSystem extends FileSystem:
  private val files = mutable.Map.empty[os.Path, String]

  def put(path: os.Path, content: String): Unit = files(path) = content
  def get(path: os.Path): Option[String] = files.get(path)
  def all: Map[os.Path, String] = files.toMap

  def exists(path: os.Path): Boolean = files.contains(path)

  def read(path: os.Path): Either[String, String] =
    files.get(path) match
      case Some(content) => Right(content)
      case None          => Left(s"File not found: $path")

  def write(path: os.Path, content: String): Either[String, Unit] =
    files(path) = content
    Right(())

/** Scriptable git fake. Default behavior covers the happy path; tests can
  * override individual state values (current branch, head SHA, pushed branches,
  * branches that already exist).
  */
final class FakeGit(
    initialBranch: String,
    initialHeadSha: String
) extends GitOps:
  private val currentBranch: AtomicReference[String] =
    AtomicReference(initialBranch)
  private val headSha: AtomicReference[String] = AtomicReference(initialHeadSha)
  private val branches: mutable.Set[String] = mutable.Set(initialBranch)
  private val pushedBranches: mutable.Set[String] = mutable.Set.empty
  private val commitMessages: mutable.ArrayBuffer[String] =
    mutable.ArrayBuffer.empty

  /** Behavior overrides for failure-injection scenarios. */
  private val pushResultRef: AtomicReference[Either[String, Unit]] =
    AtomicReference(Right(()))
  private val createBranchResultRef
      : AtomicReference[Option[Either[String, Unit]]] = AtomicReference(None)
  private val stagingCheckRef: AtomicReference[StagingCheck] =
    AtomicReference(StagingCheck(Nil, Nil, Nil))
  private val commitResultRef: AtomicReference[Option[Either[String, String]]] =
    AtomicReference(None)
  private val diffFilesRef: AtomicReference[List[String]] =
    AtomicReference(Nil)
  private val stagedPaths: mutable.ArrayBuffer[os.Path] =
    mutable.ArrayBuffer.empty

  def setPushResult(result: Either[String, Unit]): Unit =
    pushResultRef.set(result)
  def setCreateBranchResult(result: Option[Either[String, Unit]]): Unit =
    createBranchResultRef.set(result)
  def setStagingCheck(check: StagingCheck): Unit = stagingCheckRef.set(check)
  def setCommitResult(result: Either[String, String]): Unit =
    commitResultRef.set(Some(result))
  def setDiffFiles(files: List[String]): Unit = diffFilesRef.set(files)
  def stagedPathList: List[os.Path] = stagedPaths.toList

  def getCurrentBranch(dir: os.Path): Either[String, String] =
    Right(currentBranch.get())

  def getFullHeadSha(dir: os.Path): Either[String, String] =
    Right(headSha.get())

  def push(
      branch: String,
      dir: os.Path,
      setUpstream: Boolean
  ): Either[String, Unit] =
    pushResultRef.get().map { _ =>
      pushedBranches += branch
      ()
    }

  def createAndCheckoutBranch(
      name: String,
      dir: os.Path
  ): Either[String, Unit] =
    createBranchResultRef
      .get()
      .getOrElse:
        if branches.contains(name) then
          Left(s"Failed to create branch '$name': already exists")
        else
          branches += name
          currentBranch.set(name)
          Right(())

  def commitFileWithRetry(
      path: os.Path,
      message: String,
      dir: os.Path
  ): Either[String, String] =
    commitMessages += message
    val newSha = s"fake-sha-after-${commitMessages.size}"
    headSha.set(newSha)
    Right(newSha)

  def setCurrentBranch(name: String): Unit =
    currentBranch.set(name)
    branches += name

  def setHeadSha(sha: String): Unit = headSha.set(sha)
  def addExistingBranch(name: String): Unit = branches += name

  def wasPushed(branch: String): Boolean = pushedBranches.contains(branch)
  def currentBranchName: String = currentBranch.get()
  def committedMessages: List[String] = commitMessages.toList

  def getStagingCheck(dir: os.Path): Either[String, StagingCheck] =
    Right(stagingCheckRef.get())

  def stageFiles(paths: Seq[os.Path], dir: os.Path): Either[String, Unit] =
    stagedPaths ++= paths
    Right(())

  def commit(message: String, dir: os.Path): Either[String, String] =
    commitResultRef
      .get()
      .getOrElse:
        commitMessages += message
        val newSha = s"fake-commit-${commitMessages.size}"
        headSha.set(newSha)
        Right(newSha)

  def diffNameOnly(
      baseline: String,
      dir: os.Path
  ): Either[String, List[String]] =
    Right(diffFilesRef.get())

/** Wraps FakeFileSystem and runs the real `ReviewStateUpdater.merge` +
  * `ReviewStateValidator.validate` pipeline. Mirrors `ReviewStateAdapter` but
  * stays in-memory.
  */
final class FakeReviewStateOps(fs: FakeFileSystem) extends ReviewStateOps:
  def update(
      path: os.Path,
      input: ReviewStateUpdater.UpdateInput
  ): Either[String, Unit] =
    for
      existing <- fs.read(path)
      merged = ReviewStateUpdater.merge(existing, input)
      validation = ReviewStateValidator.validate(merged)
      _ <-
        if validation.isValid then Right(())
        else
          val errorMessages = validation.errors
            .map(e => s"  ${e.field}: ${e.message}")
            .mkString("\n")
          Left(s"Validation failed after merge:\n$errorMessages")
      _ <- fs.write(path, merged)
    yield ()

/** Default-wired fake env for tests. Construct with the desired starting git
  * state; mutate the individual fakes for finer scenarios.
  */
final class FakeCommandEnv(
    val cwd: os.Path,
    val console: FakeConsole,
    val fs: FakeFileSystem,
    val git: FakeGit,
    val reviewState: FakeReviewStateOps
) extends CommandEnv

object FakeCommandEnv:
  def apply(
      cwd: os.Path = os.root / "tmp" / "fake-repo",
      initialBranch: String = "TEST-100",
      initialHeadSha: String = "0000000000000000000000000000000000000000"
  ): FakeCommandEnv =
    val console = FakeConsole()
    val fs = FakeFileSystem()
    val git = FakeGit(initialBranch, initialHeadSha)
    val reviewState = FakeReviewStateOps(fs)
    new FakeCommandEnv(cwd, console, fs, git, reviewState)
