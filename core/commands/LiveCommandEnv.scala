// PURPOSE: Production CommandEnv implementation wiring capability traits to
// PURPOSE: the existing live adapters (GitAdapter, ReviewStateAdapter, os.*)

package iw.core.commands

import iw.core.adapters.{
  GitAdapter,
  GitHubClient,
  GitLabClient,
  HookDiscovery,
  ProcessAdapter,
  ProcessResult,
  ReviewStateAdapter
}
import iw.core.model.{
  CICheckResult,
  ForgeType,
  GitRemote,
  RecoveryAction,
  ReviewStateUpdater,
  StagingCheck
}

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
  def getHeadSha(dir: os.Path): Either[String, String] =
    GitAdapter.getHeadSha(dir)

object LiveReviewStateOps extends ReviewStateOps:
  def update(
      path: os.Path,
      input: ReviewStateUpdater.UpdateInput
  ): Either[String, Unit] =
    ReviewStateAdapter.update(path, input)
  def readPrUrl(path: os.Path): Either[String, String] =
    ReviewStateAdapter.readPrUrl(path)

object LiveProcess extends Process:
  def commandExists(command: String): Boolean =
    ProcessAdapter.commandExists(command)
  def run(command: Seq[String]): ProcessResult =
    ProcessAdapter.run(command)

object LiveStdin extends Stdin:
  def read(): String = scala.io.Source.stdin.mkString

object LiveTrackerOps extends TrackerOps:
  def createPullRequest(
      forge: ForgeType,
      repository: String,
      headBranch: String,
      baseBranch: String,
      title: String,
      body: String,
      gitlabHost: Option[String]
  ): Either[String, String] =
    forge match
      case ForgeType.GitHub =>
        GitHubClient.createPullRequest(
          repository,
          headBranch,
          baseBranch,
          title,
          body
        )
      case ForgeType.GitLab =>
        GitLabClient.createMergeRequest(
          repository,
          headBranch,
          baseBranch,
          title,
          body,
          execCommand = GitLabClient.execCommandWithHost(gitlabHost)
        )

  def mergeSquashAndDelete(
      forge: ForgeType,
      prUrl: String,
      gitlabHost: Option[String]
  ): Either[String, Unit] =
    val (cmd, args, env) = forge match
      case ForgeType.GitHub =>
        (
          "gh",
          Seq("pr", "merge", "--squash", "--delete-branch", prUrl),
          Map.empty[String, String]
        )
      case ForgeType.GitLab =>
        (
          "glab",
          Seq("mr", "merge", "--squash", prUrl),
          gitlabHost.map(h => Map("GITLAB_HOST" -> h)).getOrElse(Map.empty)
        )
    val result = ProcessAdapter.run(cmd +: args, env = env)
    if result.exitCode == 0 then Right(())
    else Left(s"Failed to merge PR: ${result.stderr}")

  def mergeWithDelete(
      forge: ForgeType,
      prUrl: String,
      gitlabHost: Option[String]
  ): Either[String, Unit] =
    val (cmd, args, env) = forge match
      case ForgeType.GitHub =>
        (
          "gh",
          GitHubClient.buildMergePrWithDeleteCommand(prUrl).toSeq,
          Map.empty[String, String]
        )
      case ForgeType.GitLab =>
        (
          "glab",
          GitLabClient.buildMergeMrWithDeleteCommand(prUrl).toSeq,
          gitlabHost.map(h => Map("GITLAB_HOST" -> h)).getOrElse(Map.empty)
        )
    val result = ProcessAdapter.run(cmd +: args, env = env)
    if result.exitCode == 0 then Right(())
    else Left(s"Failed to merge PR: ${result.stderr}")

  def fetchCheckStatuses(
      forge: ForgeType,
      prNumber: Int,
      repository: String,
      gitlabHost: Option[String]
  ): Either[String, List[CICheckResult]] =
    forge match
      case ForgeType.GitHub =>
        GitHubClient.fetchCheckStatuses(prNumber, repository)
      case ForgeType.GitLab =>
        GitLabClient.fetchCheckStatuses(
          prNumber,
          repository,
          execCommand = GitLabClient.execCommandWithHost(gitlabHost)
        )

object LiveClock extends Clock:
  def now: Long = System.currentTimeMillis()
  def sleep(ms: Long): Unit = Thread.sleep(ms)

object LiveHookOps extends HookOps:
  def recoveryActions: List[RecoveryAction] =
    HookDiscovery.collectValues[RecoveryAction]

final case class LiveCommandEnv(cwd: os.Path) extends CommandEnv:
  val console: Console = LiveConsole
  val fs: FileSystem = LiveFileSystem
  val git: GitOps = LiveGitOps
  val reviewState: ReviewStateOps = LiveReviewStateOps
  val process: Process = LiveProcess
  val tracker: TrackerOps = LiveTrackerOps
  val clock: Clock = LiveClock
  val hooks: HookOps = LiveHookOps
  val stdin: Stdin = LiveStdin

object LiveCommandEnv:
  def default: LiveCommandEnv = LiveCommandEnv(os.pwd)
