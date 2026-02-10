// PURPOSE: Doctor checks for Scalafix configuration - validates .scalafix.conf
// PURPOSE: Exposes checks to verify Scalafix config file exists and has DisableSyntax rule configured

import iw.core.model.*

object ScalafixHookDoctor:
  // Check if .scalafix.conf exists
  val configExists: Check = Check(".scalafix.conf", ScalafixChecks.checkConfigExists)

  // Check if .scalafix.conf has DisableSyntax rule with required sub-rules
  val disableSyntaxRules: Check = Check(".scalafix.conf rules", ScalafixChecks.checkDisableSyntaxRules)
