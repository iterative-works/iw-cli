// PURPOSE: Doctor checks for GitHub tracker - validates gh CLI prerequisites
// PURPOSE: Exposes checks to verify gh CLI installation and authentication

import iw.core.model.*
import iw.core.adapters.*
import iw.core.output.*
import iw.core.dashboard.{GitHubHookDoctor => CoreGitHubHookDoctor}

object GitHubHookDoctor:
  // Expose checks as immutable values for hook discovery
  val ghCliCheck: Check = Check("gh CLI", CoreGitHubHookDoctor.checkGhInstalled)
  val ghAuthCheck: Check = Check("gh auth", CoreGitHubHookDoctor.checkGhAuthenticated)
