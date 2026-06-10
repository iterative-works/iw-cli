// PURPOSE: CommandEnv trait + capability sub-traits abstract the I/O dependencies
// PURPOSE: of CLI commands so command logic can run in-VM against in-memory fakes

package iw.core.commands

import iw.core.adapters.{CreatedIssue, ProcessResult, SessionHookResult}
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
  RecoveryAction,
  ReviewStateUpdater,
  ServerState,
  SessionContext,
  StagingCheck,
  WorktreeStatus
}

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
  def checkoutBranch(name: String, dir: os.Path): Either[String, Unit]
  def fetchAndReset(branch: String, dir: os.Path): Either[String, Unit]
  def getRemoteUrl(dir: os.Path): Option[GitRemote]
  def getHeadSha(dir: os.Path): Either[String, String]
  def hasUncommittedChanges(path: os.Path): Either[String, Boolean]
  def isRepository(path: os.Path): Boolean

/** Review-state read/merge/validate/write boundary. Mirrors
  * `ReviewStateAdapter`.
  */
trait ReviewStateOps:
  def update(
      path: os.Path,
      input: ReviewStateUpdater.UpdateInput
  ): Either[String, Unit]
  def readPrUrl(path: os.Path): Either[String, String]

/** Subprocess execution boundary. Mirrors `ProcessAdapter`. */
trait Process:
  def commandExists(command: String): Boolean
  def run(command: Seq[String]): ProcessResult
  def runInteractive(command: Seq[String]): Int

/** Stdin boundary so commands that take piped input remain testable. */
trait Stdin:
  def read(): String

/** Forge-agnostic PR/MR operations. Live impl delegates to `GitHubClient` /
  * `GitLabClient`; fakes script responses so command logic can be tested
  * without hitting `gh` / `glab`.
  */
trait TrackerOps:
  def createPullRequest(
      forge: ForgeType,
      repository: String,
      headBranch: String,
      baseBranch: String,
      title: String,
      body: String,
      gitlabHost: Option[String]
  ): Either[String, String]

  def mergeSquashAndDelete(
      forge: ForgeType,
      prUrl: String,
      gitlabHost: Option[String]
  ): Either[String, Unit]

  def mergeWithDelete(
      forge: ForgeType,
      prUrl: String,
      gitlabHost: Option[String]
  ): Either[String, Unit]

  def fetchCheckStatuses(
      forge: ForgeType,
      prNumber: Int,
      repository: String,
      gitlabHost: Option[String]
  ): Either[String, List[CICheckResult]]

  def createFeedbackIssue(
      repository: String,
      title: String,
      description: String,
      issueType: FeedbackParser.IssueType
  ): Either[String, CreatedIssue]

  def fetchLinearIssue(
      issueId: IssueId,
      token: ApiToken
  ): Either[String, Issue]

  def fetchYouTrackIssue(
      issueId: IssueId,
      baseUrl: String,
      token: ApiToken
  ): Either[String, Issue]

  def fetchGitHubIssue(
      issueId: String,
      repository: String
  ): Either[String, Issue]

  def fetchGitLabIssue(
      issueId: String,
      repository: String,
      gitlabHost: Option[String]
  ): Either[String, Issue]

  def createLinearIssue(
      title: String,
      description: String,
      teamId: String,
      token: ApiToken
  ): Either[String, CreatedIssue]

  def createYouTrackIssue(
      project: String,
      title: String,
      description: String,
      baseUrl: String,
      token: ApiToken
  ): Either[String, CreatedIssue]

  def createGitHubIssue(
      repository: String,
      title: String,
      description: String
  ): Either[String, CreatedIssue]

  def createGitLabIssue(
      repository: String,
      title: String,
      description: String,
      gitlabHost: Option[String]
  ): Either[String, CreatedIssue]

/** Bundle of all capabilities a command needs.
  *
  * Commands take a `CommandEnv` and return `CommandResult`. Production code
  * constructs `LiveCommandEnv`; tests construct an env composed of in-memory
  * fakes from `core/test/fixtures/`.
  */
/** Wall-clock + sleep boundary. The fake lets tests advance time without
  * actually waiting, and inspect how much sleep was requested.
  */
trait Clock:
  def now: Long
  def sleep(ms: Long): Unit

/** Plugin-hook discovery boundary. Live impl reads `IW_HOOK_CLASSES` and
  * reflectively scans for typed values; fakes hold a static list.
  */
trait HookOps:
  def recoveryActions: List[RecoveryAction]
  def runSessionHooks(ctx: SessionContext): SessionHookResult
  def discoverChecks: List[Check]
  def discoverFixActions: List[FixAction]

/** Dashboard server query boundary. Live impl hits the local server over HTTP;
  * fakes script responses.
  */
trait ServerOps:
  def getWorktreeStatus(issueId: String): Either[String, WorktreeStatus]
  def updateLastSeen(
      issueId: String,
      path: String,
      trackerType: String,
      team: String
  ): Either[String, Unit]
  def unregisterWorktree(issueId: String): Either[String, Unit]
  def registerWorktree(
      issueId: String,
      path: String,
      trackerType: String,
      team: String
  ): Either[String, Unit]
  def registerProject(
      projectName: String,
      path: String,
      trackerType: String,
      team: String,
      trackerUrl: Option[String]
  ): Either[String, Unit]

/** Tmux session boundary. Live impl invokes `tmux` via the system shell; fakes
  * track sessions in memory.
  */
trait TmuxOps:
  def isInsideTmux: Boolean
  def currentSessionName: Option[String]
  def sessionExists(name: String): Boolean
  def createSession(name: String, workDir: os.Path): Either[String, Unit]
  def attachSession(name: String): Either[String, Unit]
  def switchSession(name: String): Either[String, Unit]
  def killSession(name: String): Either[String, Unit]
  def isCurrentSession(name: String): Boolean

/** Interactive yes/no prompt boundary. Live impl reads from stdin; fakes script
  * scripted answers.
  */
trait Prompt:
  def confirm(question: String, default: Boolean): Boolean

/** Git worktree boundary. Live impl shells out to `git worktree`; fakes track
  * worktrees in memory.
  */
trait WorktreeOps:
  def exists(path: os.Path, workDir: os.Path): Boolean
  def branchExists(branchName: String, workDir: os.Path): Boolean
  def create(
      targetPath: os.Path,
      branchName: String,
      workDir: os.Path
  ): Either[String, Unit]
  def createForBranch(
      targetPath: os.Path,
      branchName: String,
      workDir: os.Path
  ): Either[String, Unit]
  def remove(
      path: os.Path,
      workDir: os.Path,
      force: Boolean
  ): Either[String, Unit]

/** Persistent state-file reader (the server's on-disk state.json). */
trait StateReader:
  def read(): Either[String, ServerState]

/** Environment-variable lookup boundary. Live impl reads `sys.env`; fakes hold
  * a scriptable map.
  */
trait EnvVars:
  def get(name: String): Option[String]

trait CommandEnv:
  def cwd: os.Path
  def console: Console
  def fs: FileSystem
  def git: GitOps
  def reviewState: ReviewStateOps
  def process: Process
  def tracker: TrackerOps
  def clock: Clock
  def hooks: HookOps
  def stdin: Stdin
  def server: ServerOps
  def state: StateReader
  def tmux: TmuxOps
  def prompt: Prompt
  def worktree: WorktreeOps
  def envVars: EnvVars
