// PURPOSE: Hook trait for worktree-removal cleanup actions in iw rm
// PURPOSE: Plugins provide CleanupAction implementations to tear down project daemons before removal

package iw.core.model

/** Context passed to cleanup hooks during `iw rm`, after safety checks pass and
  * before the worktree directory is removed.
  */
case class CleanupContext(
    worktreePath: os.Path,
    issueId: String,
    config: ProjectConfiguration,
    force: Boolean
)

/** Hook trait for teardown before worktree removal.
  *
  * Implementations shut down project-spawned processes (build daemons, dev
  * servers, docker stacks) rooted in the worktree before it is removed.
  *
  * `iw rm` runs each hook with the target worktree as the working directory, so
  * `os.pwd`, relative paths, and subprocesses spawned via `os.proc(...).call()`
  * (which defaults its `cwd` to `os.pwd`) all resolve inside the worktree.
  * `ctx.worktreePath` is the same directory, for code that needs it explicitly.
  *
  * Return contract:
  *   - `Nil` => success; `rm` proceeds.
  *   - non-empty list => warnings; each string is surfaced to the user and `rm`
  *     still proceeds.
  *   - throwing => abort; `rm` prints the error, preserves the worktree, and
  *     exits non-zero.
  */
trait CleanupAction:
  def cleanup(ctx: CleanupContext): List[String]
