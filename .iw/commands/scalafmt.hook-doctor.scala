// PURPOSE: Doctor checks for Scalafmt configuration - validates .scalafmt.conf
// PURPOSE: Exposes checks to verify Scalafmt config file exists and has version configured

import iw.core.model.*

object ScalafmtHookDoctor:
  // Check if .scalafmt.conf exists
  val configExists: Check = Check(".scalafmt.conf", ScalafmtChecks.checkConfigExists, "Quality")

  // Check if .scalafmt.conf has a version configured
  val versionExists: Check = Check(".scalafmt.conf version", ScalafmtChecks.checkVersionExists, "Quality")
