// PURPOSE: Infrastructure service for executing shell commands
// PURPOSE: Provides command execution and availability detection

package iw.core.infrastructure

import scala.sys.process._
import scala.util.{Try, Success, Failure}
import java.io.File

/** Command execution utilities for shell commands.
  * Used by services to execute git, gh, glab commands.
  */
object CommandRunner:

  /** Execute command and return stdout.
    *
    * @param command Command to execute (e.g., "git", "gh")
    * @param args Command arguments (e.g., Array("status", "--porcelain"))
    * @param workingDir Optional working directory for command execution
    * @return Right(stdout) if success, Left(error message) if failure
    */
  def execute(
    command: String,
    args: Array[String],
    workingDir: Option[String] = None
  ): Either[String, String] =
    try
      val processBuilder = workingDir match
        case Some(dir) => Process(command +: args, new File(dir))
        case None => Process(command +: args)

      val output = processBuilder.!!.trim
      Right(output)
    catch
      case e: RuntimeException if e.getMessage != null && e.getMessage.contains("exit") =>
        Left(s"Command failed: $command ${args.mkString(" ")}: ${e.getMessage}")
      case e: RuntimeException if e.getMessage != null && e.getMessage.contains("Cannot run program") =>
        Left(s"Command not found: $command")
      case e: Exception =>
        Left(s"Command error: ${e.getMessage}")

  /** Check if command is available in PATH.
    *
    * @param command Command name (e.g., "gh", "glab", "git")
    * @return true if command exists and is executable, false otherwise
    */
  def isCommandAvailable(command: String): Boolean =
    try
      s"which $command".! == 0
    catch
      case _: Exception => false
