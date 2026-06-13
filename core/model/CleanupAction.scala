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
  * Return contract:
  *   - `Nil` => success; `rm` proceeds.
  *   - non-empty list => warnings; each string is surfaced to the user and `rm`
  *     still proceeds.
  *   - throwing => abort; `rm` prints the error, preserves the worktree, and
  *     exits non-zero.
  */
trait CleanupAction:
  def cleanup(ctx: CleanupContext): List[String]
