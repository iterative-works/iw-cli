// PURPOSE: Adapter for executing shell commands and process operations
// PURPOSE: Provides commandExists to check if a command is available in PATH

package iw.core

import scala.sys.process.*

case class ProcessResult(exitCode: Int, stdout: String, stderr: String, truncated: Boolean = false)

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

  def run(command: Seq[String], maxOutputBytes: Int = 1024 * 1024): ProcessResult =
    val stdoutBuilder = new StringBuilder
    val stderrBuilder = new StringBuilder
    var stdoutBytes = 0
    var stderrBytes = 0
    var wasTruncated = false

    val logger = ProcessLogger(
      line =>
        val lineWithNewline = line + "\n"
        val lineBytes = lineWithNewline.getBytes(Constants.Encoding.Utf8).length
        if stdoutBytes + lineBytes <= maxOutputBytes then
          stdoutBuilder.append(lineWithNewline)
          stdoutBytes += lineBytes
        else
          wasTruncated = true
      ,
      line =>
        val lineWithNewline = line + "\n"
        val lineBytes = lineWithNewline.getBytes(Constants.Encoding.Utf8).length
        if stderrBytes + lineBytes <= maxOutputBytes then
          stderrBuilder.append(lineWithNewline)
          stderrBytes += lineBytes
        else
          wasTruncated = true
    )

    val exitCode = command.!(logger)

    ProcessResult(
      exitCode = exitCode,
      stdout = stdoutBuilder.toString.trim,
      stderr = stderrBuilder.toString.trim,
      truncated = wasTruncated
    )
