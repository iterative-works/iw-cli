// PURPOSE: Scalafmt doctor check functions - validates .scalafmt.conf presence and version
// PURPOSE: Provides checkConfigExists and checkVersionExists for doctor hooks
package iw.core.model

object ScalafmtChecks:

  /** Check if .scalafmt.conf exists in project root.
    *
    * @param config Project configuration
    * @param fileExists Function to check file existence (injected for testability)
    * @return CheckResult indicating .scalafmt.conf presence
    */
  def checkConfigExistsWith(
    config: ProjectConfiguration,
    fileExists: os.Path => Boolean
  ): CheckResult =
    val scalafmtConf = os.pwd / ".scalafmt.conf"
    if fileExists(scalafmtConf) then
      CheckResult.Success("Found")
    else
      CheckResult.Error("Missing", "Create .scalafmt.conf in project root")

  /** Check if .scalafmt.conf exists (uses real file system).
    *
    * @param config Project configuration
    * @return CheckResult indicating .scalafmt.conf presence
    */
  def checkConfigExists(config: ProjectConfiguration): CheckResult =
    checkConfigExistsWith(config, os.exists(_))

  /** Check if .scalafmt.conf has a version key configured.
    *
    * @param config Project configuration
    * @param readFile Function to read file contents (injected for testability)
    * @return CheckResult indicating version configuration status
    */
  def checkVersionExistsWith(
    config: ProjectConfiguration,
    readFile: os.Path => Option[String]
  ): CheckResult =
    val scalafmtConf = os.pwd / ".scalafmt.conf"
    readFile(scalafmtConf) match
      case None =>
        CheckResult.Error("Cannot read file", "Ensure .scalafmt.conf exists and is readable")
      case Some(content) =>
        // Look for version = "..." or version='...' or version="..."
        val versionPattern = """version\s*=\s*['"].*['"]""".r
        if versionPattern.findFirstIn(content).isDefined then
          CheckResult.Success("Configured")
        else
          CheckResult.WarningWithHint("Version not specified", "Add 'version = \"3.x.x\"' to .scalafmt.conf")

  /** Check if .scalafmt.conf has a version key configured (uses real file system).
    *
    * @param config Project configuration
    * @return CheckResult indicating version configuration status
    */
  def checkVersionExists(config: ProjectConfiguration): CheckResult =
    checkVersionExistsWith(config, path =>
      try
        Some(os.read(path))
      catch
        case _: Exception => None
    )
