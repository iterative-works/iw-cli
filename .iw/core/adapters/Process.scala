// PURPOSE: Adapter for executing shell commands and process operations
// PURPOSE: Provides commandExists to check if a command is available in PATH

package iw.core.adapters

import iw.core.model.Constants

case class ProcessResult(
  exitCode: Int,
  stdout: String,
  stderr: String,
  truncated: Boolean = false,
  timedOut: Boolean = false
)

object ProcessAdapter:
  // Safe pattern: only alphanumeric, dash, underscore
  private val SafeCommandPattern = "^[a-zA-Z0-9_-]+$".r
  private val DefaultTimeoutMs = 5 * 60 * 1000 // 5 minutes

  def commandExists(command: String): Boolean =
    // Validate command name to prevent injection (defense in depth)
    if !SafeCommandPattern.matches(command) then
      return false

    try
      val result = os.proc("which", command).call(
        check = false,
        stdout = os.Pipe,
        stderr = os.Pipe
      )
      result.exitCode == 0
    catch
      case _: Exception => false

  // SIGTERM exit code (128 + 15) indicates process was killed, likely by timeout
  private val SigtermExitCode = 143

  def run(
    command: Seq[String],
    maxOutputBytes: Int = 1024 * 1024,
    timeoutMs: Int = DefaultTimeoutMs
  ): ProcessResult =
    val result = os.proc(command).call(
      check = false,
      stdout = os.Pipe,
      stderr = os.Pipe,
      timeout = timeoutMs
    )

    val (stdout, stdoutTruncated) = truncateOutput(result.out.text().trim, maxOutputBytes)
    val (stderr, stderrTruncated) = truncateOutput(result.err.text().trim, maxOutputBytes)
    val timedOut = result.exitCode == SigtermExitCode

    ProcessResult(
      exitCode = result.exitCode,
      stdout = stdout,
      stderr = stderr,
      truncated = stdoutTruncated || stderrTruncated,
      timedOut = timedOut
    )

  def runStreaming(command: Seq[String], timeoutMs: Int = DefaultTimeoutMs): Int =
    try
      val result = os.proc(command).call(
        check = false,
        stdout = os.Inherit,
        stderr = os.Inherit,
        timeout = timeoutMs
      )
      result.exitCode
    catch
      case _: os.SubprocessException => -1

  private def truncateOutput(output: String, maxBytes: Int): (String, Boolean) =
    val bytes = output.getBytes(Constants.Encoding.Utf8)
    if bytes.length <= maxBytes then
      (output, false)
    else
      // Truncate at byte boundary, being careful with UTF-8
      val truncated = new String(bytes.take(maxBytes), Constants.Encoding.Utf8)
      (truncated, true)
