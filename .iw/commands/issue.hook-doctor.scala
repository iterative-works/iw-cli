// PURPOSE: Doctor check for issue command - validates Linear API token
// PURPOSE: Exposes check to verify LINEAR_API_TOKEN environment variable

import iw.core.*

object IssueHookDoctor:
  // Pure function - easily testable in isolation
  def checkLinearToken(config: ProjectConfiguration): CheckResult =
    if config.trackerType != IssueTrackerType.Linear then
      CheckResult.Skip("Not using Linear")
    else sys.env.get("LINEAR_API_TOKEN") match
      case None =>
        CheckResult.Error("Not set", Some("export LINEAR_API_TOKEN=lin_api_..."))
      case Some(token) if token.isEmpty =>
        CheckResult.Error("Empty", Some("export LINEAR_API_TOKEN=lin_api_..."))
      case Some(token) =>
        if LinearClient.validateToken(token) then
          CheckResult.Success("Valid")
        else
          CheckResult.Error("Authentication failed", Some("Check token at linear.app/settings/api"))

  // Expose check as immutable value for discovery
  val check: Check = Check("LINEAR_API_TOKEN", checkLinearToken)
