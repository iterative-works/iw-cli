// PURPOSE: Doctor checks for CONTRIBUTING.md - validates file presence and section coverage
// PURPOSE: Exposes checks to verify CONTRIBUTING.md exists and covers key topics

import iw.core.model.*

object ContributingHookDoctor:
  // Check if CONTRIBUTING.md exists
  val fileExists: Check = Check("CONTRIBUTING.md", ContributingChecks.checkFileExists)

  // Check if CONTRIBUTING.md has sections covering CI, hooks, and local checks
  val sectionsCovered: Check = Check("CONTRIBUTING.md sections", ContributingChecks.checkSectionsCovered)
