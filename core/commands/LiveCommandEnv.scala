// PURPOSE: Production CommandEnv implementation wiring capability traits to
// PURPOSE: the existing live adapters (GitAdapter, ReviewStateAdapter, os.*)

package iw.core.commands

import iw.core.adapters.{
  ConfigFileRepository,
  CreatedIssue,
  GitAdapter,
  GitHubClient,
  GitLabClient,
  HookDiscovery,
  LinearClient,
  ProcessAdapter,
  ProcessManager,
  ProcessResult,
  ReviewStateAdapter,
  ServerClient,
  ServerConfigRepository,
  SessionHookResult,
  SessionHooks,
  StateReader as StateReaderAdapter,
  TmuxAdapter,
  YouTrackClient
}
import iw.core.model.{
  ApiToken,
  Check,
  CICheckResult,
  FeedbackParser,
  FixAction,
  ForgeType,
  GitRemote,
  Issue,
  IssueId,
  ProjectConfiguration,
  RecoveryAction,
  ReviewStateUpdater,
  ServerConfig,
  ServerState,
  SessionContext,
  StagingCheck,
  WorktreeStatus
}
import scala.util.Try
import sttp.client4.quick.*

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
  def makeDirAll(path: os.Path): Either[String, Unit] =
    try
      os.makeDir.all(path)
      Right(())
    catch case e: Exception => Left(s"Failed to create $path: ${e.getMessage}")

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
  def isRepository(path: os.Path): Boolean =
    GitAdapter.isGitRepository(path)

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

  def createFeedbackIssue(
      repository: String,
      title: String,
      description: String,
      issueType: FeedbackParser.IssueType
  ): Either[String, CreatedIssue] =
    GitHubClient.createIssue(repository, title, description, issueType)

  def fetchLinearIssue(
      issueId: IssueId,
      token: ApiToken
  ): Either[String, Issue] =
    LinearClient.fetchIssue(issueId, token)

  def fetchYouTrackIssue(
      issueId: IssueId,
      baseUrl: String,
      token: ApiToken
  ): Either[String, Issue] =
    YouTrackClient.fetchIssue(issueId, baseUrl, token)

  def fetchGitHubIssue(
      issueId: String,
      repository: String
  ): Either[String, Issue] =
    GitHubClient.fetchIssue(issueId, repository)

  def fetchGitLabIssue(
      issueId: String,
      repository: String,
      gitlabHost: Option[String]
  ): Either[String, Issue] =
    GitLabClient.fetchIssue(
      issueId,
      repository,
      execCommand = GitLabClient.execCommandWithHost(gitlabHost)
    )

  def createLinearIssue(
      title: String,
      description: String,
      teamId: String,
      token: ApiToken
  ): Either[String, CreatedIssue] =
    LinearClient.createIssue(title, description, teamId, token)

  def createYouTrackIssue(
      project: String,
      title: String,
      description: String,
      baseUrl: String,
      token: ApiToken
  ): Either[String, CreatedIssue] =
    YouTrackClient.createIssue(project, title, description, baseUrl, token)

  def createGitHubIssue(
      repository: String,
      title: String,
      description: String
  ): Either[String, CreatedIssue] =
    GitHubClient.createIssue(repository, title, description)

  def createGitLabIssue(
      repository: String,
      title: String,
      description: String,
      gitlabHost: Option[String]
  ): Either[String, CreatedIssue] =
    GitLabClient.createIssue(
      repository,
      title,
      description,
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

  def discoverChecks: List[Check] =
    HookDiscovery.collectValues[Check]

  def discoverFixActions: List[FixAction] =
    HookDiscovery.collectValues[FixAction]

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

object LiveEnvVars extends EnvVars:
  def get(name: String): Option[String] = sys.env.get(name)

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
  def ask(question: String): String =
    iw.core.adapters.Prompt.ask(question)

object LiveServerConfigOps extends ServerConfigOps:
  def getOrCreateDefault(path: String): Either[String, ServerConfig] =
    ServerConfigRepository.getOrCreateDefault(path)
  def write(config: ServerConfig, path: String): Either[String, Unit] =
    ServerConfigRepository.write(config, path)

object LiveProcessLifecycle extends ProcessLifecycle:
  def readPidFile(path: String): Either[String, Option[Long]] =
    ProcessManager.readPidFile(path)
  def writePidFile(pid: Long, path: String): Either[String, Unit] =
    ProcessManager.writePidFile(pid, path)
  def removePidFile(path: String): Either[String, Unit] =
    ProcessManager.removePidFile(path)
  def isProcessAlive(pid: Long): Boolean =
    ProcessManager.isProcessAlive(pid)
  def spawnServerProcess(
      statePath: String,
      port: Int,
      hosts: Seq[String]
  ): Either[String, Long] =
    ProcessManager.spawnServerProcess(statePath, port, hosts)
  def stopProcess(pid: Long, timeoutSeconds: Int): Either[String, Unit] =
    ProcessManager.stopProcess(pid, timeoutSeconds)
  def serverLogPath(statePath: String): String =
    ProcessManager.serverLogPath(statePath)
  def waitForHealth(
      healthUrl: String,
      attempts: Int,
      intervalMs: Long
  ): Boolean =
    @annotation.tailrec
    def loop(remaining: Int): Boolean =
      if remaining <= 0 then false
      else
        Thread.sleep(intervalMs)
        val ok = Try {
          val response =
            quickRequest.get(sttp.model.Uri.unsafeParse(healthUrl)).send()
          response.code.code == 200
        }.getOrElse(false)
        if ok then true else loop(remaining - 1)
    loop(attempts)
  def fetchJson(url: String): Either[String, String] =
    Try {
      val response = quickRequest.get(sttp.model.Uri.unsafeParse(url)).send()
      if response.code.code == 200 then Right(response.body)
      else Left(s"HTTP ${response.code.code}")
    }.fold(e => Left(s"Network error: ${e.getMessage}"), identity)

object LiveDashboardLifecycle extends DashboardLifecycle:
  def isServerRunning(healthUrl: String): Boolean =
    Try {
      val response =
        quickRequest.get(sttp.model.Uri.unsafeParse(healthUrl)).send()
      response.code.code == 200
    }.getOrElse(false)

  def runSync(cmd: Seq[String]): Int =
    val pb = new ProcessBuilder(cmd*)
    pb.inheritIO()
    val process = pb.start()
    process.waitFor()

  def startServerAndBlock(
      cmd: Seq[String],
      healthUrl: String,
      timeoutMs: Long,
      onReady: () => Unit
  ): Either[String, Int] =
    val pb = new ProcessBuilder(cmd*)
    pb.inheritIO()
    val process = pb.start()
    val start = System.currentTimeMillis()
    @annotation.tailrec
    def poll(): Boolean =
      if System.currentTimeMillis() - start >= timeoutMs then false
      else if isServerRunning(healthUrl) then true
      else
        Thread.sleep(200)
        poll()
    if poll() then
      onReady()
      Right(process.waitFor())
    else
      process.destroy()
      Left("Server failed to start within timeout")

  def openBrowser(url: String): Unit =
    val osName = System.getProperty("os.name").toLowerCase
    val command =
      if osName.contains("mac") then Some(Seq("open", url))
      else if osName.contains("nix") || osName.contains("nux") || osName
          .contains("aix")
      then Some(Seq("xdg-open", url))
      else if osName.contains("win") then Some(Seq("cmd", "/c", "start", url))
      else None
    command.foreach { cmd =>
      Try(new ProcessBuilder(cmd*).start())
    }

  def findAvailablePort(): Int =
    val socket = new java.net.ServerSocket(0)
    val port = socket.getLocalPort
    socket.close()
    port

object LiveConfigOps extends ConfigOps:
  def read(path: os.Path): Either[String, ProjectConfiguration] =
    ConfigFileRepository.read(path) match
      case Some(c) => Right(c)
      case None    =>
        Left(s"Cannot read configuration at $path")
  def write(
      path: os.Path,
      config: ProjectConfiguration
  ): Either[String, Unit] =
    try
      ConfigFileRepository.write(path, config)
      Right(())
    catch case e: Exception => Left(s"Failed to write config: ${e.getMessage}")

object LiveWorktreeOps extends WorktreeOps:
  def exists(path: os.Path, workDir: os.Path): Boolean =
    iw.core.adapters.GitWorktreeAdapter.worktreeExists(path, workDir)
  def branchExists(branchName: String, workDir: os.Path): Boolean =
    iw.core.adapters.GitWorktreeAdapter.branchExists(branchName, workDir)
  def create(
      targetPath: os.Path,
      branchName: String,
      workDir: os.Path
  ): Either[String, Unit] =
    iw.core.adapters.GitWorktreeAdapter.createWorktree(
      targetPath,
      branchName,
      workDir
    )
  def createForBranch(
      targetPath: os.Path,
      branchName: String,
      workDir: os.Path
  ): Either[String, Unit] =
    iw.core.adapters.GitWorktreeAdapter.createWorktreeForBranch(
      targetPath,
      branchName,
      workDir
    )
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
  val envVars: EnvVars = LiveEnvVars
  val config: ConfigOps = LiveConfigOps
  val serverConfig: ServerConfigOps = LiveServerConfigOps
  val dashboard: DashboardLifecycle = LiveDashboardLifecycle
  val processLifecycle: ProcessLifecycle = LiveProcessLifecycle

object LiveCommandEnv:
  def default: LiveCommandEnv = LiveCommandEnv(os.pwd)
