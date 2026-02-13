// PURPOSE: Tmux adapter for session management operations
// PURPOSE: Provides session creation, existence checks, and cleanup

package iw.core.adapters

import iw.core.model.Constants

object TmuxAdapter:
  /** Socket args for tmux isolation (empty when using default server) */
  private def socketArgs: Seq[String] =
    sys.env.get(Constants.EnvVars.TmuxSocket) match
      case Some(socket) if socket.nonEmpty => Seq("-L", socket)
      case _ => Seq.empty

  /** Build a tmux command with optional socket */
  private def tmuxCmd(args: String*): Seq[String] =
    Seq("tmux") ++ socketArgs ++ args

  /** Check if a tmux session with the given name exists */
  def sessionExists(name: String): Boolean =
    ProcessAdapter.run(tmuxCmd("has-session", "-t", name)).exitCode == 0

  /** Create a new tmux session in the given directory */
  def createSession(name: String, workDir: os.Path): Either[String, Unit] =
    // Unset IW_* environment variables (except IW_HOME and IW_TMUX_SOCKET) to prevent leakage
    // These are internal plumbing for running iw commands, not session state
    val cleanEnv = sys.env.filter { case (key, _) =>
      key == "IW_HOME" || key == Constants.EnvVars.TmuxSocket || !key.startsWith("IW_")
    }

    val result = os.proc(tmuxCmd("new-session", "-d", "-s", name, "-c", workDir.toString))
      .call(
        check = false,
        stdout = os.Pipe,
        stderr = os.Pipe,
        env = cleanEnv
      )

    if result.exitCode == 0 then Right(())
    else Left(s"Failed to create tmux session: ${result.err.text().trim}")

  /** Attach to an existing tmux session */
  def attachSession(name: String): Either[String, Unit] =
    // Use runStreaming to pass terminal through (attach needs real terminal)
    val exitCode = ProcessAdapter.runStreaming(tmuxCmd("attach-session", "-t", name))
    if exitCode == 0 then Right(())
    else Left(s"Failed to attach to session '$name'")

  /** Switch to an existing tmux session (when already inside tmux) */
  def switchSession(name: String): Either[String, Unit] =
    // Use runStreaming to pass terminal through (switch needs real terminal)
    val exitCode = ProcessAdapter.runStreaming(tmuxCmd("switch-client", "-t", name))
    if exitCode == 0 then Right(())
    else Left(s"Failed to switch to session '$name'")

  /** Kill an existing tmux session */
  def killSession(name: String): Either[String, Unit] =
    val result = ProcessAdapter.run(tmuxCmd("kill-session", "-t", name))
    if result.exitCode == 0 then Right(())
    else Left(s"Failed to kill session: ${result.stderr}")

  /** Check if currently running inside a tmux session on our managed server */
  def isInsideTmux: Boolean =
    sys.env.get(Constants.EnvVars.Tmux) match
      case None => false
      case Some(tmuxVar) =>
        // When using a custom socket, only consider "inside tmux" if TMUX env var
        // references the same socket. TMUX format: /path/to/socket,pid,pane
        sys.env.get(Constants.EnvVars.TmuxSocket) match
          case Some(socket) if socket.nonEmpty =>
            tmuxVar.split(",").headOption.exists(_.endsWith(s"/$socket"))
          case _ => true

  /** Get current tmux session name if inside tmux */
  def currentSessionName: Option[String] =
    if !isInsideTmux then None
    else
      val result = ProcessAdapter.run(tmuxCmd("display-message", "-p", "#S"))
      if result.exitCode == 0 then Some(result.stdout.trim)
      else None

  /** Check if the given session name matches the current session */
  def isCurrentSession(sessionName: String): Boolean =
    currentSessionName.contains(sessionName)
