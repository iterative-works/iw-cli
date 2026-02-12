// PURPOSE: Git hooks doctor check functions - validates hook directory, files, and installation
// PURPOSE: Provides checkHooksDirExists, checkHookFilesExist, and checkHooksInstalled for doctor hooks
package iw.core.model

object GitHooksChecks:

  /** Check if .git-hooks/ directory exists in project root.
    *
    * @param config Project configuration
    * @param dirExists Function to check directory existence (injected for testability)
    * @return CheckResult indicating .git-hooks/ presence
    */
  def checkHooksDirExistsWith(
    config: ProjectConfiguration,
    dirExists: os.Path => Boolean
  ): CheckResult =
    val hooksDir = os.pwd / ".git-hooks"
    if dirExists(hooksDir) then
      CheckResult.Success("Found")
    else
      CheckResult.Error("Missing", "Create .git-hooks/ directory in project root")

  /** Check if .git-hooks/ directory exists (uses real file system).
    *
    * @param config Project configuration
    * @return CheckResult indicating .git-hooks/ presence
    */
  def checkHooksDirExists(config: ProjectConfiguration): CheckResult =
    checkHooksDirExistsWith(config, os.isDir(_))

  /** Check if pre-commit and pre-push hook files exist in .git-hooks/.
    *
    * @param config Project configuration
    * @param fileExists Function to check file existence (injected for testability)
    * @return CheckResult indicating hook files presence
    */
  def checkHookFilesExistWith(
    config: ProjectConfiguration,
    fileExists: os.Path => Boolean
  ): CheckResult =
    val hooksDir = os.pwd / ".git-hooks"
    val preCommit = hooksDir / "pre-commit"
    val prePush = hooksDir / "pre-push"

    val missing = List(
      ("pre-commit", preCommit),
      ("pre-push", prePush)
    ).filterNot { case (_, path) => fileExists(path) }
      .map(_._1)

    if missing.isEmpty then
      CheckResult.Success("Found")
    else
      CheckResult.Error(s"Missing: ${missing.mkString(", ")}", "Create missing hook files in .git-hooks/")

  /** Check if pre-commit and pre-push hook files exist (uses real file system).
    *
    * @param config Project configuration
    * @return CheckResult indicating hook files presence
    */
  def checkHookFilesExist(config: ProjectConfiguration): CheckResult =
    checkHookFilesExistWith(config, os.exists(_))

  /** Check if hooks are installed (symlinked or via core.hooksPath).
    *
    * @param config Project configuration
    * @param getGitDir Function to get git directory path (injected for testability)
    * @param getHooksPath Function to get core.hooksPath config (injected for testability)
    * @param readSymlink Function to read symlink target (injected for testability)
    * @return CheckResult indicating hook installation status
    */
  def checkHooksInstalledWith(
    config: ProjectConfiguration,
    getGitDir: () => Option[os.Path],
    getHooksPath: () => Option[String],
    readSymlink: os.Path => Option[os.Path]
  ): CheckResult =
    // First check if core.hooksPath is set to .git-hooks
    getHooksPath() match
      case Some(hooksPath) if hooksPath == ".git-hooks" =>
        CheckResult.Success("Installed")
      case _ =>
        // Fall back to checking symlinks
        getGitDir() match
          case None =>
            CheckResult.Skip("Cannot determine git directory")
          case Some(gitDir) =>
            val hookNames = List("pre-commit", "pre-push")
            val expectedTargets = hookNames.map(name => os.pwd / ".git-hooks" / name)
            val installedHooks = hookNames.zip(expectedTargets).map { case (name, expectedTarget) =>
              val hookPath = gitDir / "hooks" / name
              readSymlink(hookPath) match
                case Some(target) if target == expectedTarget => true
                case _ => false
            }

            if installedHooks.forall(identity) then
              CheckResult.Success("Installed")
            else
              CheckResult.WarningWithHint("Not installed", "Run: git config core.hooksPath .git-hooks")

  /** Check if hooks are installed (uses real file system and git command).
    *
    * @param config Project configuration
    * @return CheckResult indicating hook installation status
    */
  def checkHooksInstalled(config: ProjectConfiguration): CheckResult =
    val getGitDir = () =>
      try
        val result = os.proc("git", "rev-parse", "--git-dir").call(cwd = os.pwd)
        val gitDirStr = result.out.trim()
        // git rev-parse can return relative or absolute path
        val gitDir = if gitDirStr.startsWith("/") then
          os.Path(gitDirStr)
        else
          os.pwd / os.RelPath(gitDirStr)
        Some(gitDir)
      catch
        case _: Exception => None

    val getHooksPath = () =>
      try
        val result = os.proc("git", "config", "core.hooksPath").call(cwd = os.pwd)
        Some(result.out.trim())
      catch
        case _: Exception => None

    val readSymlink = (path: os.Path) =>
      try
        if os.isLink(path) then
          val target = os.readLink(path)
          // Resolve relative symlink to absolute path
          val absoluteTarget = target match
            case relPath: os.RelPath => (path / os.up / relPath)
            case absPath: os.Path => absPath
            case subPath: os.SubPath => (path / os.up / subPath)
          Some(absoluteTarget)
        else
          None
      catch
        case _: Exception => None

    checkHooksInstalledWith(config, getGitDir, getHooksPath, readSymlink)
