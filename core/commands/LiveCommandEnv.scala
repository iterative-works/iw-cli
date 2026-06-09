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
  ReviewStateAdapter,
  ServerClient,
  SessionHookResult,
  SessionHooks,
  StateReader as StateReaderAdapter,
  TmuxAdapter
}
import iw.core.model.{
  CICheckResult,
  ForgeType,
  GitRemote,
  RecoveryAction,
  ReviewStateUpdater,
  ServerState,
  SessionContext,
  StagingCheck,
  WorktreeStatus
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
  def hasUncommittedChanges(path: os.Path): Either[String, Boolean] =
    GitAdapter.hasUncommittedChanges(path)

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
  def runInteractive(command: Seq[String]): Int =
    ProcessAdapter.runInteractive(command)

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

  def runSessionHooks(ctx: SessionContext): SessionHookResult =
    SessionHooks.run(ctx)

object LiveServerOps extends ServerOps:
  def getWorktreeStatus(issueId: String): Either[String, WorktreeStatus] =
    ServerClient.getWorktreeStatus(issueId)

  def updateLastSeen(
      issueId: String,
      path: String,
      trackerType: String,
      team: String
  ): Either[String, Unit] =
    ServerClient.updateLastSeen(issueId, path, trackerType, team)

  def unregisterWorktree(issueId: String): Either[String, Unit] =
    ServerClient.unregisterWorktree(issueId)

  def registerWorktree(
      issueId: String,
      path: String,
      trackerType: String,
      team: String
  ): Either[String, Unit] =
    ServerClient.registerWorktree(issueId, path, trackerType, team)

  def registerProject(
      projectName: String,
      path: String,
      trackerType: String,
      team: String,
      trackerUrl: Option[String]
  ): Either[String, Unit] =
    ServerClient.registerProject(
      projectName,
      path,
      trackerType,
      team,
      trackerUrl
    )

object LiveStateReader extends StateReader:
  def read(): Either[String, ServerState] = StateReaderAdapter.read()

object LiveTmuxOps extends TmuxOps:
  def isInsideTmux: Boolean = TmuxAdapter.isInsideTmux
  def currentSessionName: Option[String] = TmuxAdapter.currentSessionName
  def sessionExists(name: String): Boolean = TmuxAdapter.sessionExists(name)
  def createSession(name: String, workDir: os.Path): Either[String, Unit] =
    TmuxAdapter.createSession(name, workDir)
  def attachSession(name: String): Either[String, Unit] =
    TmuxAdapter.attachSession(name)
  def switchSession(name: String): Either[String, Unit] =
    TmuxAdapter.switchSession(name)
  def killSession(name: String): Either[String, Unit] =
    TmuxAdapter.killSession(name)
  def isCurrentSession(name: String): Boolean =
    TmuxAdapter.isCurrentSession(name)

object LivePrompt extends Prompt:
  def confirm(question: String, default: Boolean): Boolean =
    iw.core.adapters.Prompt.confirm(question, default)

object LiveWorktreeOps extends WorktreeOps:
  def exists(path: os.Path, workDir: os.Path): Boolean =
    iw.core.adapters.GitWorktreeAdapter.worktreeExists(path, workDir)
  def remove(
      path: os.Path,
      workDir: os.Path,
      force: Boolean
  ): Either[String, Unit] =
    iw.core.adapters.GitWorktreeAdapter.removeWorktree(path, workDir, force)

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
  val server: ServerOps = LiveServerOps
  val state: StateReader = LiveStateReader
  val tmux: TmuxOps = LiveTmuxOps
  val prompt: Prompt = LivePrompt
  val worktree: WorktreeOps = LiveWorktreeOps

object LiveCommandEnv:
  def default: LiveCommandEnv = LiveCommandEnv(os.pwd)
