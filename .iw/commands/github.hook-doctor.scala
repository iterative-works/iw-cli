// PURPOSE: Doctor checks for GitHub tracker - validates gh CLI prerequisites
// PURPOSE: Exposes checks to verify gh CLI installation and authentication

import iw.core.*
import iw.core.infrastructure.{GitHubHookDoctor => CoreGitHubHookDoctor}

object GitHubHookDoctor:
  // Expose checks as immutable values for hook discovery
  val ghCliCheck: Check = Check("gh CLI", CoreGitHubHookDoctor.checkGhInstalled)
  val ghAuthCheck: Check = Check("gh auth", CoreGitHubHookDoctor.checkGhAuthenticated)
