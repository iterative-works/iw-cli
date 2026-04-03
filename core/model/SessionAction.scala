// PURPOSE: Hook traits for session lifecycle in start/open commands
// PURPOSE: SessionSetup runs init commands, SessionAction provides the main session command

package iw.core.model

/** Context passed to session hooks after worktree + tmux session setup.
  */
case class SessionContext(
    sessionName: String,
    worktreePath: os.Path,
    issueId: String,
    prompt: Option[String]
)

/** Hook trait for session setup commands (e.g., direnv allow, env init).
  *
  * Multiple SessionSetup hooks may exist; all returned commands are executed
  * sequentially before the main SessionAction.
  */
trait SessionSetup:
  def run(ctx: SessionContext): Option[String]

/** Hook trait for the main session command (e.g., launching an editor or CLI
  * tool).
  *
  * At most one SessionAction may return a command. If multiple do, it is an
  * error.
  */
trait SessionAction:
  def run(ctx: SessionContext): Option[String]
