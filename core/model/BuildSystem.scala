// PURPOSE: Build system detection for quality gate fix remediation
// PURPOSE: Detects Mill, SBT, or scala-cli based on project files

package iw.core.model

enum BuildSystem:
  case Mill, SBT, ScalaCli, Unknown

object BuildSystem:
  def detectWith(fileExists: os.Path => Boolean): BuildSystem =
    val root = os.pwd
    if fileExists(root / "build.mill") then BuildSystem.Mill
    else if fileExists(root / "build.sbt") then BuildSystem.SBT
    else if fileExists(root / "project.scala") then BuildSystem.ScalaCli
    else BuildSystem.Unknown

  def detect(): BuildSystem = detectWith(os.exists(_))
