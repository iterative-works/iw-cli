// PURPOSE: Adapter for executing shell commands and process operations
// PURPOSE: Provides commandExists to check if a command is available in PATH

package iw.core

import scala.sys.process.*

case class ProcessResult(exitCode: Int, stdout: String, stderr: String)

object ProcessAdapter:
  // Safe pattern: only alphanumeric, dash, underscore
  private val SafeCommandPattern = "^[a-zA-Z0-9_-]+$".r

  def commandExists(command: String): Boolean =
    // Validate command name to prevent injection (defense in depth)
    if !SafeCommandPattern.matches(command) then
      return false

    try
      // Use 'which' via Process API - no shell invocation, command is passed as argument
      // This avoids shell injection by not using string interpolation into a shell command
      val process = Process(Seq("which", command))
      val exitCode = process.!(ProcessLogger(_ => (), _ => ()))
      exitCode == 0
    catch
      case _: Exception => false

  def run(command: Seq[String]): ProcessResult =
    val stdoutBuilder = new StringBuilder
    val stderrBuilder = new StringBuilder

    val logger = ProcessLogger(
      line => stdoutBuilder.append(line).append("\n"),
      line => stderrBuilder.append(line).append("\n")
    )

    val exitCode = command.!(logger)

    ProcessResult(
      exitCode = exitCode,
      stdout = stdoutBuilder.toString.trim,
      stderr = stderrBuilder.toString.trim
    )
