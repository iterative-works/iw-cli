// PURPOSE: Doctor check for start command - validates tmux installation
// PURPOSE: Exposes check to verify tmux is available in PATH

import iw.core.model.*
import iw.core.adapters.*
import iw.core.output.*

object StartHookDoctor:
  // Pure function - easily testable in isolation
  def checkTmux(config: ProjectConfiguration): CheckResult =
    if ProcessAdapter.commandExists("tmux") then
      CheckResult.Success("Installed")
    else
      CheckResult.Error("Not found", "Install: sudo apt install tmux (Debian/Ubuntu) or brew install tmux (macOS)")

  // Expose check as immutable value for discovery
  val check: Check = Check("tmux", checkTmux)
