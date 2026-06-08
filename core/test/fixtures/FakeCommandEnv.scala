// PURPOSE: In-memory fakes implementing CommandEnv capability traits for harness tests
// PURPOSE: FakeConsole captures stdout/stderr; FakeFileSystem + FakeGit + FakeReviewState track state

package iw.core.test.fixtures

import iw.core.adapters.ProcessResult
import iw.core.commands.{
  Clock,
  CommandEnv,
  Console,
  FileSystem,
  GitOps,
  HookOps,
  Process,
  ReviewStateOps,
  TrackerOps
}
import iw.core.model.{
  CICheckResult,
  ForgeType,
  GitRemote,
  RecoveryAction,
  ReviewStateUpdater,
  ReviewStateValidator,
  StagingCheck
}

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
  private val checkoutResultRef: AtomicReference[Option[Either[String, Unit]]] =
    AtomicReference(None)
  private val fetchAndResetResultRef: AtomicReference[Either[String, Unit]] =
    AtomicReference(Right(()))
  private val remoteUrlRef: AtomicReference[Option[GitRemote]] =
    AtomicReference(None)
  private val resetBranches: mutable.ArrayBuffer[String] =
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
  def setCheckoutResult(result: Option[Either[String, Unit]]): Unit =
    checkoutResultRef.set(result)
  def setFetchAndResetResult(result: Either[String, Unit]): Unit =
    fetchAndResetResultRef.set(result)
  def setRemoteUrl(remote: Option[GitRemote]): Unit = remoteUrlRef.set(remote)
  def resetBranchList: List[String] = resetBranches.toList

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

  def checkoutBranch(name: String, dir: os.Path): Either[String, Unit] =
    checkoutResultRef
      .get()
      .getOrElse:
        if !branches.contains(name) then
          Left(s"Failed to checkout branch '$name': not found")
        else
          currentBranch.set(name)
          Right(())

  def fetchAndReset(branch: String, dir: os.Path): Either[String, Unit] =
    fetchAndResetResultRef.get().map { _ =>
      resetBranches += branch
      ()
    }

  def getRemoteUrl(dir: os.Path): Option[GitRemote] = remoteUrlRef.get()

/** Scriptable subprocess fake. By default `commandExists` returns true and
  * `run` returns an exit-0 empty result. Tests can scriptResponses by command
  * (exact-match on the first arg) or override globally.
  */
final class FakeProcess extends Process:
  private val existingCommands: mutable.Set[String] =
    mutable.Set("git", "gh", "glab", "scala-cli", "tmux")
  private val responseScript: mutable.Map[Seq[String], ProcessResult] =
    mutable.Map.empty
  private val invocations: mutable.ArrayBuffer[Seq[String]] =
    mutable.ArrayBuffer.empty
  private val defaultResultRef: AtomicReference[ProcessResult] =
    AtomicReference(ProcessResult(0, "", ""))

  def setExistingCommands(names: Set[String]): Unit =
    existingCommands.clear()
    existingCommands ++= names
  def markCommandMissing(name: String): Unit = existingCommands -= name
  def setDefaultResult(result: ProcessResult): Unit =
    defaultResultRef.set(result)

  /** Pin a result for a specific command prefix. The longest matching prefix
    * wins; useful for `gh pr list --merged` vs `gh pr list --open`.
    */
  def scriptResponse(commandPrefix: Seq[String], result: ProcessResult): Unit =
    responseScript(commandPrefix) = result

  def commandExists(command: String): Boolean =
    existingCommands.contains(command)

  def run(command: Seq[String]): ProcessResult =
    invocations += command
    val match_ = responseScript
      .filter((prefix, _) => command.startsWith(prefix))
      .toSeq
      .sortBy(-_._1.length)
      .headOption
      .map(_._2)
    match_.getOrElse(defaultResultRef.get())

  def invocationList: List[Seq[String]] = invocations.toList

/** Scriptable tracker fake. Records every
  * createPullRequest/mergeSquashAndDelete call so tests can assert what was
  * sent to the forge.
  */
final class FakeTracker extends TrackerOps:
  final case class PrCall(
      forge: ForgeType,
      repository: String,
      headBranch: String,
      baseBranch: String,
      title: String,
      body: String,
      gitlabHost: Option[String]
  )
  final case class MergeCall(
      forge: ForgeType,
      prUrl: String,
      gitlabHost: Option[String]
  )

  private val prCalls: mutable.ArrayBuffer[PrCall] = mutable.ArrayBuffer.empty
  private val mergeCalls: mutable.ArrayBuffer[MergeCall] =
    mutable.ArrayBuffer.empty
  private val createPrResultRef: AtomicReference[Either[String, String]] =
    AtomicReference(Right("https://example.com/pr/1"))
  private val mergeResultRef: AtomicReference[Either[String, Unit]] =
    AtomicReference(Right(()))

  def setCreatePrResult(result: Either[String, String]): Unit =
    createPrResultRef.set(result)
  def setMergeResult(result: Either[String, Unit]): Unit =
    mergeResultRef.set(result)

  def createPullRequest(
      forge: ForgeType,
      repository: String,
      headBranch: String,
      baseBranch: String,
      title: String,
      body: String,
      gitlabHost: Option[String]
  ): Either[String, String] =
    prCalls += PrCall(
      forge,
      repository,
      headBranch,
      baseBranch,
      title,
      body,
      gitlabHost
    )
    createPrResultRef.get()

  def mergeSquashAndDelete(
      forge: ForgeType,
      prUrl: String,
      gitlabHost: Option[String]
  ): Either[String, Unit] =
    mergeCalls += MergeCall(forge, prUrl, gitlabHost)
    mergeResultRef.get()

  private val mergeWithDeleteResultRef: AtomicReference[Either[String, Unit]] =
    AtomicReference(Right(()))
  private val mergeWithDeleteCalls: mutable.ArrayBuffer[MergeCall] =
    mutable.ArrayBuffer.empty
  private val checkStatusQueue
      : mutable.Queue[Either[String, List[CICheckResult]]] =
    mutable.Queue.empty
  private val checkStatusDefault
      : AtomicReference[Either[String, List[CICheckResult]]] =
    AtomicReference(Right(Nil))
  private val checkStatusCalls: mutable.ArrayBuffer[Int] =
    mutable.ArrayBuffer.empty

  def setMergeWithDeleteResult(result: Either[String, Unit]): Unit =
    mergeWithDeleteResultRef.set(result)
  def setCheckStatuses(result: Either[String, List[CICheckResult]]): Unit =
    checkStatusDefault.set(result)
  def queueCheckStatuses(results: Either[String, List[CICheckResult]]*): Unit =
    checkStatusQueue ++= results
  def checkStatusCallCount: Int = checkStatusCalls.size

  def mergeWithDelete(
      forge: ForgeType,
      prUrl: String,
      gitlabHost: Option[String]
  ): Either[String, Unit] =
    mergeWithDeleteCalls += MergeCall(forge, prUrl, gitlabHost)
    mergeWithDeleteResultRef.get()

  def fetchCheckStatuses(
      forge: ForgeType,
      prNumber: Int,
      repository: String,
      gitlabHost: Option[String]
  ): Either[String, List[CICheckResult]] =
    checkStatusCalls += prNumber
    if checkStatusQueue.nonEmpty then checkStatusQueue.dequeue()
    else checkStatusDefault.get()

  def prCallList: List[PrCall] = prCalls.toList
  def mergeCallList: List[MergeCall] = mergeCalls.toList
  def mergeWithDeleteCallList: List[MergeCall] = mergeWithDeleteCalls.toList

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

  def readPrUrl(path: os.Path): Either[String, String] =
    for
      json <- fs.read(path)
      url <-
        try
          val parsed = ujson.read(json)
          if parsed.obj.contains("pr_url") && !parsed("pr_url").isNull then
            Right(parsed("pr_url").str)
          else
            Left(
              "No pr_url found in review-state.json. Run 'iw phase-pr' first."
            )
        catch
          case e: Exception =>
            Left(
              s"Failed to read pr_url from review-state.json: ${e.getMessage}"
            )
    yield url

/** Advance manually-controlled wall clock and record sleeps. Tests can pre-load
  * a sequence of `now` values to step through polling iterations without
  * blocking the suite.
  */
final class FakeClock(initial: Long = 0L) extends Clock:
  private val nowRef: AtomicReference[Long] = AtomicReference(initial)
  private val sleeps: mutable.ArrayBuffer[Long] = mutable.ArrayBuffer.empty
  private val nowQueue: mutable.Queue[Long] = mutable.Queue.empty

  def setNow(value: Long): Unit = nowRef.set(value)
  def advance(deltaMs: Long): Unit = nowRef.updateAndGet(_ + deltaMs)
  def queueNowSequence(values: Long*): Unit = nowQueue ++= values
  def sleepCalls: List[Long] = sleeps.toList

  def now: Long =
    if nowQueue.nonEmpty then nowQueue.dequeue()
    else nowRef.get()

  def sleep(ms: Long): Unit =
    sleeps += ms

/** Plug-in-hook fake. Tests set the list explicitly; no reflection. */
final class FakeHookOps extends HookOps:
  private val actionsRef: AtomicReference[List[RecoveryAction]] =
    AtomicReference(Nil)
  def setRecoveryActions(list: List[RecoveryAction]): Unit =
    actionsRef.set(list)
  def recoveryActions: List[RecoveryAction] = actionsRef.get()

/** Default-wired fake env for tests. Construct with the desired starting git
  * state; mutate the individual fakes for finer scenarios.
  */
final class FakeCommandEnv(
    val cwd: os.Path,
    val console: FakeConsole,
    val fs: FakeFileSystem,
    val git: FakeGit,
    val reviewState: FakeReviewStateOps,
    val process: FakeProcess,
    val tracker: FakeTracker,
    val clock: FakeClock,
    val hooks: FakeHookOps
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
    val process = FakeProcess()
    val tracker = FakeTracker()
    val clock = FakeClock()
    val hooks = FakeHookOps()
    new FakeCommandEnv(
      cwd,
      console,
      fs,
      git,
      reviewState,
      process,
      tracker,
      clock,
      hooks
    )
