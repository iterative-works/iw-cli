// PURPOSE: Scalafix doctor check functions - validates .scalafix.conf presence and DisableSyntax rule
// PURPOSE: Provides checkConfigExists and checkDisableSyntaxRules for doctor hooks
package iw.core.model

object ScalafixChecks:

  /** Check if .scalafix.conf exists in project root.
    *
    * @param config Project configuration
    * @param fileExists Function to check file existence (injected for testability)
    * @return CheckResult indicating .scalafix.conf presence
    */
  def checkConfigExistsWith(
    config: ProjectConfiguration,
    fileExists: os.Path => Boolean
  ): CheckResult =
    val scalafixConf = os.pwd / ".scalafix.conf"
    if fileExists(scalafixConf) then
      CheckResult.Success("Found")
    else
      CheckResult.Error("Missing", "Create .scalafix.conf in project root")

  /** Check if .scalafix.conf exists (uses real file system).
    *
    * @param config Project configuration
    * @return CheckResult indicating .scalafix.conf presence
    */
  def checkConfigExists(config: ProjectConfiguration): CheckResult =
    checkConfigExistsWith(config, os.exists(_))

  /** Check if .scalafix.conf has DisableSyntax rule with required sub-rules configured.
    *
    * Required sub-rules: noNulls, noVars, noThrows, noReturns
    *
    * @param config Project configuration
    * @param readFile Function to read file contents (injected for testability)
    * @return CheckResult indicating DisableSyntax rule configuration status
    */
  def checkDisableSyntaxRulesWith(
    config: ProjectConfiguration,
    readFile: os.Path => Option[String]
  ): CheckResult =
    val scalafixConf = os.pwd / ".scalafix.conf"
    readFile(scalafixConf) match
      case None =>
        CheckResult.Error("Cannot read file", "Ensure .scalafix.conf exists and is readable")
      case Some(content) =>
        // Check if DisableSyntax rule is present
        if !content.contains("DisableSyntax") then
          CheckResult.WarningWithHint("DisableSyntax not configured", "Add DisableSyntax rule to .scalafix.conf")
        else
          // Check for required sub-rules
          val requiredRules = List("noNulls", "noVars", "noThrows", "noReturns")
          val missingRules = requiredRules.filterNot(rule => content.contains(rule))

          if missingRules.isEmpty then
            CheckResult.Success("Configured")
          else
            CheckResult.WarningWithHint(
              s"Missing rules: ${missingRules.sorted.mkString(", ")}",
              "Add missing rules to DisableSyntax in .scalafix.conf"
            )

  /** Check if .scalafix.conf has DisableSyntax rule with required sub-rules (uses real file system).
    *
    * @param config Project configuration
    * @return CheckResult indicating DisableSyntax rule configuration status
    */
  def checkDisableSyntaxRules(config: ProjectConfiguration): CheckResult =
    checkDisableSyntaxRulesWith(config, path =>
      try
        Some(os.read(path))
      catch
        case _: Exception => None
    )
