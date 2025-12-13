// PURPOSE: Adapter for executing shell commands and process operations
// PURPOSE: Provides commandExists to check if a command is available in PATH

//> using scala 3.3.1

package iw.core

import scala.sys.process.*

object ProcessAdapter:
  // Safe pattern: only alphanumeric, dash, underscore
  private val SafeCommandPattern = "^[a-zA-Z0-9_-]+$".r

  def commandExists(command: String): Boolean =
    // Validate command name to prevent shell injection
    if !SafeCommandPattern.matches(command) then
      return false

    try
      // Use 'command -v' which is POSIX-compliant and returns 0 if command exists
      // Redirect output to /dev/null to suppress stdout/stderr
      val exitCode = s"sh -c 'command -v $command >/dev/null 2>&1'".!
      exitCode == 0
    catch
      case _: Exception => false
