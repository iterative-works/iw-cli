// PURPOSE: Infrastructure for background process spawning and PID file management
// PURPOSE: Handles process lifecycle control with UNIX signals and process detection

package iw.core.dashboard

import scala.util.{Try, Success, Failure}
import java.nio.file.{Files, Paths, NoSuchFileException}

object ProcessManager:

  /** Write PID to file, creating parent directories if needed */
  def writePidFile(pid: Long, path: String): Either[String, Unit] =
    Try {
      val osPath = os.Path(path)
      // Create parent directories if they don't exist
      os.makeDir.all(osPath / os.up)

      os.write.over(osPath, pid.toString)
    } match
      case Success(_) => Right(())
      case Failure(e) => Left(s"Failed to write PID file: ${e.getMessage}")

  /** Read PID from file, returns None if file doesn't exist */
  def readPidFile(path: String): Either[String, Option[Long]] =
    Try {
      val osPath = os.Path(path)
      if !os.exists(osPath) then
        None
      else
        val content = os.read(osPath).trim
        if content.isEmpty then
          throw new IllegalArgumentException("PID file is empty")

        val pid = content.toLong
        Some(pid)
    } match
      case Success(maybePid) => Right(maybePid)
      case Failure(e: NoSuchFileException) => Right(None)
      case Failure(e: NumberFormatException) => Left(s"Invalid PID in file: ${e.getMessage}")
      case Failure(e: IllegalArgumentException) => Left(e.getMessage)
      case Failure(e) => Left(s"Failed to read PID file: ${e.getMessage}")

  /** Check if a process with given PID is alive */
  def isProcessAlive(pid: Long): Boolean =
    Try {
      ProcessHandle.of(pid).map(_.isAlive()).orElse(false)
    } match
      case Success(alive) => alive
      case Failure(_) => false

  /** Remove PID file */
  def removePidFile(path: String): Either[String, Unit] =
    Try {
      val osPath = os.Path(path)
      if os.exists(osPath) then
        os.remove(osPath)
    } match
      case Success(_) => Right(())
      case Failure(e) => Left(s"Failed to remove PID file: ${e.getMessage}")

  /** Stop process with SIGTERM and wait for exit */
  def stopProcess(pid: Long, timeoutSeconds: Int = 10): Either[String, Unit] =
    Try {
      val processHandle = ProcessHandle.of(pid)

      if processHandle.isEmpty then
        Right(()) // Process already dead
      else
        val process = processHandle.get()

        if !process.isAlive() then
          Right(()) // Process already dead
        else
          // Send SIGTERM (destroy is SIGTERM on Unix)
          process.destroy()

          // Wait for process to exit
          var waited = 0
          while process.isAlive() && waited < timeoutSeconds do
            Thread.sleep(1000)
            waited += 1

          if process.isAlive() then
            Left(s"Process $pid did not exit after $timeoutSeconds seconds")
          else
            Right(())
    } match
      case Success(result) => result
      case Failure(e) => Left(s"Failed to stop process: ${e.getMessage}")

  /**
   * Spawn server process in background
   * Returns the PID of the spawned process
   */
  def spawnServerProcess(statePath: String, port: Int, hosts: Seq[String]): Either[String, Long] =
    Try {
      // Use scala-cli to run the server-daemon script with core library
      val hostsArg = hosts.mkString(",")
      val coreDir = os.Path(
        sys.env.getOrElse("IW_CORE_DIR", (os.pwd / ".iw" / "core").toString)
      )
      val coreFiles = os.walk(coreDir)
        .filter(p => p.ext == "scala" && !p.toString.contains("/test/"))
        .map(_.toString)
        .toSeq

      val commandsDir = os.Path(
        sys.env.getOrElse("IW_COMMANDS_DIR", (os.pwd / ".iw" / "commands").toString)
      )
      val daemonScript = (commandsDir / "server-daemon.scala").toString
      val command = Seq("scala-cli", "run", daemonScript) ++
        coreFiles ++
        Seq("--main-class", "iw.core.dashboard.ServerDaemon", "--", statePath, port.toString, hostsArg)

      val processBuilder = new ProcessBuilder(command: _*)

      // Redirect output to avoid blocking
      // TODO: Consider logging to a file instead of discarding
      processBuilder.redirectOutput(ProcessBuilder.Redirect.DISCARD)
      processBuilder.redirectError(ProcessBuilder.Redirect.DISCARD)

      val process = processBuilder.start()
      val pid = process.pid()

      pid
    } match
      case Success(pid) => Right(pid)
      case Failure(e) => Left(s"Failed to spawn server process: ${e.getMessage}")
