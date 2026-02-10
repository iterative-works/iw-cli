// PURPOSE: Generate remediation prompt for Claude Code doctor fix
// PURPOSE: Assembles prompt from failed checks, build system, and CI platform

package iw.core.model

object FixPrompt:
  def generate(
    failedChecks: List[String],
    buildSystem: BuildSystem,
    ciPlatform: String
  ): String =
    if failedChecks.isEmpty then return ""

    val buildSystemName = buildSystem match
      case BuildSystem.Mill => "Mill"
      case BuildSystem.SBT => "SBT"
      case BuildSystem.ScalaCli => "scala-cli"
      case BuildSystem.Unknown => "Unknown"

    val checkList = failedChecks.map(check => s"- $check").mkString("\n")

    s"""You are helping set up quality gates for a Scala project.

The following quality gate checks are currently failing:

$checkList

Detected build system: $buildSystemName
CI platform: $ciPlatform

Please help set up each missing quality gate by:

1. Installing necessary tools/plugins for the build system
2. Adding configuration files (e.g., .scalafmt.conf, .scalafix.conf, wartremover settings)
3. Setting up CI workflow files for $ciPlatform to run these checks
4. Following the build system's conventions for where config files should be located

For each failed check, provide:
- Required dependencies/plugins
- Configuration file content
- CI workflow integration
- Any build file changes needed

Make the setup idiomatic for $buildSystemName and compatible with $ciPlatform."""
