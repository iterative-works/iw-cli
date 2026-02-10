// PURPOSE: CONTRIBUTING.md doctor check functions - validates file presence and section coverage
// PURPOSE: Provides checkFileExistsWith and checkSectionsCoveredWith for doctor hooks
package iw.core.model

object ContributingChecks:

  /** Check if CONTRIBUTING.md exists in project root.
    *
    * @param config Project configuration
    * @param fileExists Function to check file existence (injected for testability)
    * @return CheckResult indicating CONTRIBUTING.md presence
    */
  def checkFileExistsWith(
    config: ProjectConfiguration,
    fileExists: os.Path => Boolean
  ): CheckResult =
    val contributingMd = os.pwd / "CONTRIBUTING.md"
    if fileExists(contributingMd) then
      CheckResult.Success("Found")
    else
      CheckResult.Warning("Missing")

  /** Check if CONTRIBUTING.md exists (uses real file system).
    *
    * @param config Project configuration
    * @return CheckResult indicating CONTRIBUTING.md presence
    */
  def checkFileExists(config: ProjectConfiguration): CheckResult =
    checkFileExistsWith(config, os.exists(_))

  /** Check if CONTRIBUTING.md covers key topics using case-insensitive keyword matching.
    *
    * Topics checked:
    * - CI: continuous integration, pipeline, workflow, github actions, gitlab ci
    * - Hooks: hook, pre-commit, pre-push, git-hooks
    * - Local checks: local, locally, running checks, run checks
    *
    * @param config Project configuration
    * @param readFile Function to read file contents (injected for testability)
    * @return CheckResult indicating section coverage status
    */
  def checkSectionsCoveredWith(
    config: ProjectConfiguration,
    readFile: os.Path => Option[String]
  ): CheckResult =
    val contributingMd = os.pwd / "CONTRIBUTING.md"
    readFile(contributingMd) match
      case None =>
        CheckResult.Error("Cannot read file", "Ensure CONTRIBUTING.md exists and is readable")
      case Some(content) =>
        val contentLower = content.toLowerCase

        // Define topic keywords (case-insensitive matching)
        val ciKeywords = Seq("ci", "continuous integration", "pipeline", "workflow", "github actions", "gitlab ci")
        val hookKeywords = Seq("hook", "pre-commit", "pre-push", "git-hooks")
        val localKeywords = Seq("local", "locally", "running checks", "run checks")

        // Check if any keyword from each topic is present
        val hasCi = ciKeywords.exists(kw => contentLower.contains(kw))
        val hasHooks = hookKeywords.exists(kw => contentLower.contains(kw))
        val hasLocal = localKeywords.exists(kw => contentLower.contains(kw))

        // Collect missing topics
        val missing = List(
          if !hasCi then Some("CI") else None,
          if !hasHooks then Some("hooks") else None,
          if !hasLocal then Some("local checks") else None
        ).flatten

        if missing.isEmpty then
          CheckResult.Success("Complete")
        else
          val missingStr = missing.mkString(", ")
          CheckResult.WarningWithHint(s"Missing: $missingStr", s"Add sections covering: $missingStr")

  /** Check if CONTRIBUTING.md covers key topics (uses real file system).
    *
    * @param config Project configuration
    * @return CheckResult indicating section coverage status
    */
  def checkSectionsCovered(config: ProjectConfiguration): CheckResult =
    checkSectionsCoveredWith(config, path =>
      try
        Some(os.read(path))
      catch
        case _: Exception => None
    )
