// PURPOSE: Tmux adapter for session management operations
// PURPOSE: Provides session creation, existence checks, and cleanup

package iw.core.adapters

import iw.core.model.Constants

object TmuxAdapter:
  /** Check if a tmux session with the given name exists */
  def sessionExists(name: String): Boolean =
    ProcessAdapter.run(Seq("tmux", "has-session", "-t", name)).exitCode == 0

  /** Create a new tmux session in the given directory */
  def createSession(name: String, workDir: os.Path): Either[String, Unit] =
    val result = ProcessAdapter.run(
      Seq("tmux", "new-session", "-d", "-s", name, "-c", workDir.toString)
    )
    if result.exitCode == 0 then Right(())
    else Left(s"Failed to create tmux session: ${result.stderr}")

  /** Attach to an existing tmux session */
  def attachSession(name: String): Either[String, Unit] =
    val result = ProcessAdapter.run(Seq("tmux", "attach-session", "-t", name))
    if result.exitCode == 0 then Right(())
    else Left(s"Failed to attach to session: ${result.stderr}")

  /** Switch to an existing tmux session (when already inside tmux) */
  def switchSession(name: String): Either[String, Unit] =
    val result = ProcessAdapter.run(Seq("tmux", "switch-client", "-t", name))
    if result.exitCode == 0 then Right(())
    else Left(s"Failed to switch to session: ${result.stderr}")

  /** Kill an existing tmux session */
  def killSession(name: String): Either[String, Unit] =
    val result = ProcessAdapter.run(Seq("tmux", "kill-session", "-t", name))
    if result.exitCode == 0 then Right(())
    else Left(s"Failed to kill session: ${result.stderr}")

  /** Check if currently running inside a tmux session */
  def isInsideTmux: Boolean =
    sys.env.contains(Constants.EnvVars.Tmux)

  /** Get current tmux session name if inside tmux */
  def currentSessionName: Option[String] =
    if !isInsideTmux then None
    else
      val result = ProcessAdapter.run(Seq("tmux", "display-message", "-p", "#S"))
      if result.exitCode == 0 then Some(result.stdout.trim)
      else None

  /** Check if the given session name matches the current session */
  def isCurrentSession(sessionName: String): Boolean =
    currentSessionName.contains(sessionName)
