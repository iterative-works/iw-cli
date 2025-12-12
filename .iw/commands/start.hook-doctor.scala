// PURPOSE: Doctor check for start command - validates tmux installation
// PURPOSE: Registers check to verify tmux is available in PATH

//> using file "../core/DoctorChecks.scala"
//> using file "../core/Config.scala"
//> using file "../core/Process.scala"

import iw.core.*

object StartHookDoctor:
  // Pure function - easily testable in isolation
  def checkTmux(config: ProjectConfiguration): CheckResult =
    if ProcessAdapter.commandExists("tmux") then
      CheckResult.Success("Installed")
    else
      CheckResult.Error("Not found", Some("Install: sudo apt install tmux (Debian/Ubuntu) or brew install tmux (macOS)"))

  // Registration happens when object is initialized
  DoctorChecks.register("tmux")(checkTmux)

// Force object initialization
val _ = StartHookDoctor
