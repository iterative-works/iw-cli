// PURPOSE: Tmux adapter for session management operations
// PURPOSE: Provides session creation, existence checks, and cleanup

//> using scala 3.3.1
//> using file "Process.scala"

package iw.core

import java.nio.file.Path

object TmuxAdapter:
  /** Check if a tmux session with the given name exists */
  def sessionExists(name: String): Boolean =
    ProcessAdapter.run(Seq("tmux", "has-session", "-t", name)).exitCode == 0

  /** Create a new tmux session in the given directory */
  def createSession(name: String, workDir: Path): Either[String, Unit] =
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

  /** Kill an existing tmux session */
  def killSession(name: String): Either[String, Unit] =
    val result = ProcessAdapter.run(Seq("tmux", "kill-session", "-t", name))
    if result.exitCode == 0 then Right(())
    else Left(s"Failed to kill session: ${result.stderr}")

  /** Check if currently running inside a tmux session */
  def isInsideTmux: Boolean =
    sys.env.contains("TMUX")

  /** Get current tmux session name if inside tmux */
  def currentSessionName: Option[String] =
    if !isInsideTmux then None
    else
      val result = ProcessAdapter.run(Seq("tmux", "display-message", "-p", "#S"))
      if result.exitCode == 0 then Some(result.stdout.trim)
      else None
