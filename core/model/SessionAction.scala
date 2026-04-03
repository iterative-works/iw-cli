// PURPOSE: Hook trait for post-session actions in start/open commands
// PURPOSE: Plugins provide SessionAction implementations to send commands to tmux sessions

package iw.core.model

/** Context passed to session action hooks after worktree + tmux session setup.
  */
case class SessionContext(
    sessionName: String,
    worktreePath: os.Path,
    issueId: String,
    prompt: Option[String]
)

/** Hook trait for post-session behavior in start/open commands.
  *
  * Implementations return an optional tmux command to send to the session.
  */
trait SessionAction:
  def run(ctx: SessionContext): Option[String]
