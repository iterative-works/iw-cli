// PURPOSE: Adapter for executing shell commands and process operations
// PURPOSE: Provides commandExists to check if a command is available in PATH

package iw.core

import scala.sys.process.*

object ProcessAdapter:
  def commandExists(command: String): Boolean =
    try
      // Use 'command -v' which is POSIX-compliant and returns 0 if command exists
      // Redirect output to /dev/null to suppress stdout/stderr
      val exitCode = s"sh -c 'command -v $command >/dev/null 2>&1'".!
      exitCode == 0
    catch
      case _: Exception => false
