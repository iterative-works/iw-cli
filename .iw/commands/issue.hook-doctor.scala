// PURPOSE: Doctor check for issue command - validates Linear API token
// PURPOSE: Exposes check to verify LINEAR_API_TOKEN environment variable

import iw.core.model.*
import iw.core.adapters.*
import iw.core.output.*
import iw.core.dashboard.{Check, CheckResult}

object IssueHookDoctor:
  // Pure function - easily testable in isolation
  def checkLinearToken(config: ProjectConfiguration): CheckResult =
    if config.trackerType != IssueTrackerType.Linear then
      CheckResult.Skip("Not using Linear")
    else ApiToken.fromEnv(Constants.EnvVars.LinearApiToken) match
      case None =>
        CheckResult.Error("Not set", s"export ${Constants.EnvVars.LinearApiToken}=lin_api_...")
      case Some(token) =>
        if LinearClient.validateToken(token) then
          CheckResult.Success("Valid")
        else
          CheckResult.Error("Authentication failed", "Check token at linear.app/settings/api")

  // Expose check as immutable value for discovery
  val check: Check = Check(Constants.EnvVars.LinearApiToken, checkLinearToken)
