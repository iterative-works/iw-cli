// PURPOSE: Doctor checks for Git hooks configuration - validates hook directory, files, and installation
// PURPOSE: Exposes checks to verify .git-hooks/ dir, hook files, and installation status

import iw.core.model.*

object GitHooksHookDoctor:
  // Check if .git-hooks/ directory exists
  val hooksDir: Check = Check("Git hooks dir", GitHooksChecks.checkHooksDirExists)

  // Check if pre-commit and pre-push hook files exist
  val hookFiles: Check = Check("Git hook files", GitHooksChecks.checkHookFilesExist)

  // Check if hooks are installed in git hooks directory
  val hooksInstalled: Check = Check("Git hooks installed", GitHooksChecks.checkHooksInstalled)
