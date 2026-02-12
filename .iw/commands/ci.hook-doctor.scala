// PURPOSE: Doctor checks for CI workflow configuration - validates CI workflow file presence
// PURPOSE: Exposes checks to verify CI workflow file exists based on tracker type

import iw.core.model.*

object CIHookDoctor:
  // Check if CI workflow file exists (platform-specific based on tracker type)
  val workflowExists: Check = Check("CI workflow", CIChecks.checkWorkflowExists, "Quality")
