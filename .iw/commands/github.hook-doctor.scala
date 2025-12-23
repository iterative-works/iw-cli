// PURPOSE: Doctor checks for GitHub tracker - validates gh CLI prerequisites
// PURPOSE: Exposes checks to verify gh CLI installation and authentication

import iw.core.*

object GitHubHookDoctor:
  // Expose checks as immutable values for hook discovery
  val ghCliCheck: Check = Check("gh CLI", iw.core.GitHubHookDoctor.checkGhInstalled)
  val ghAuthCheck: Check = Check("gh auth", iw.core.GitHubHookDoctor.checkGhAuthenticated)
