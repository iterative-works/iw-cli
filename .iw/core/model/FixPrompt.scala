// PURPOSE: Generate remediation prompt for Claude Code doctor fix
// PURPOSE: Assembles check-specific guidance with build-system-aware commands

package iw.core.model

object FixPrompt:
  def generate(
    failedChecks: List[String],
    buildSystem: BuildSystem,
    ciPlatform: String
  ): String =
    if failedChecks.isEmpty then ""
    else
      val commands = buildCommands(buildSystem)
      val checkSections = failedChecks.flatMap(checkGuidance(_, commands, ciPlatform))

      s"""You are helping set up quality gates for a Scala project.
The following quality gate checks are currently failing:
${failedChecks.map(c => s"- $c").mkString("\n")}

Detected build system: ${commands.name}
CI platform: $ciPlatform

${checkSections.mkString("\n\n")}

IMPORTANT:
- Install hooks via: git config core.hooksPath .git-hooks
- All hook scripts must be executable (chmod +x)
- Hook scripts should use set -euo pipefail and have colored output for clarity"""

  private case class Commands(
    name: String,
    checkFormat: String,
    reformat: String,
    compile: String,
    test: String,
    fix: String,
    fixCheck: String,
    cleanCompile: String
  )

  private def buildCommands(buildSystem: BuildSystem): Commands =
    buildSystem match
      case BuildSystem.Mill => Commands(
        name = "Mill",
        checkFormat = "./mill __.checkFormat",
        reformat = "./mill __.reformat",
        compile = "./mill __.compile",
        test = "./mill __.test",
        fix = "./mill __.fix",
        fixCheck = "./mill __.fix --check",
        cleanCompile = "./mill clean __.compile && ./mill __.compile"
      )
      case BuildSystem.SBT => Commands(
        name = "SBT",
        checkFormat = "sbt scalafmtCheckAll",
        reformat = "sbt scalafmtAll",
        compile = "sbt compile",
        test = "sbt test",
        fix = "sbt scalafixAll",
        fixCheck = "sbt 'scalafixAll --check'",
        cleanCompile = "sbt clean compile"
      )
      case BuildSystem.ScalaCli => Commands(
        name = "scala-cli",
        checkFormat = "scala-cli fmt --check .",
        reformat = "scala-cli fmt .",
        compile = "scala-cli compile .",
        test = "scala-cli test .",
        fix = "scalafix",
        fixCheck = "scalafix --check",
        cleanCompile = "scala-cli clean . && scala-cli compile ."
      )
      case BuildSystem.Unknown => Commands(
        name = "Unknown",
        checkFormat = "<check-format-command>",
        reformat = "<reformat-command>",
        compile = "<compile-command>",
        test = "<test-command>",
        fix = "<fix-command>",
        fixCheck = "<fix-check-command>",
        cleanCompile = "<clean-compile-command>"
      )

  private def checkGuidance(
    checkName: String,
    commands: Commands,
    ciPlatform: String
  ): Option[String] =
    checkName match

      case ".scalafmt.conf" | ".scalafmt.conf version" =>
        Some(scalafmtGuidance(commands))

      case ".scalafix.conf" | ".scalafix.conf rules" =>
        Some(scalafixGuidance(commands))

      case s if s.startsWith("Git hook") =>
        Some(gitHooksGuidance(commands))

      case "CI workflow" =>
        Some(ciGuidance(commands, ciPlatform))

      case s if s.startsWith("CONTRIBUTING") =>
        Some(contributingGuidance(commands))

      case _ => None

  private def scalafmtGuidance(commands: Commands): String =
    s"""=== .scalafmt.conf ===
Create .scalafmt.conf with Scala 3 settings:

```
version = "3.7.17"
runner.dialect = scala3
maxColumn = 100
indent.main = 4
indent.callSite = 4
newlines.source = keep
rewrite.scala3.convertToNewSyntax = true
rewrite.scala3.removeOptionalBraces = yes
rewrite.scala3.insertEndMarkerMinLines = 5
```

Verify with: ${commands.checkFormat}
Auto-fix with: ${commands.reformat}"""

  private def scalafixGuidance(commands: Commands): String =
    s"""=== .scalafix.conf ===
Create .scalafix.conf enforcing FP principles:

```
rules = [
  DisableSyntax
]

DisableSyntax {
  noNulls = true
  noVars = true
  noThrows = true
  noReturns = true
}
```

${if commands.name == "Mill" then
  "Add ScalafixModule trait to build modules (requires com.goyeau::mill-scalafix plugin)."
else if commands.name == "SBT" then
  "Add sbt-scalafix plugin to project/plugins.sbt."
else ""}
Verify with: ${commands.fixCheck}"""

  private def gitHooksGuidance(commands: Commands): String =
    s"""=== Git Hooks ===
Create .git-hooks/ directory with two hook scripts:

1. .git-hooks/pre-commit — fast format check (~10s), blocks commit on failure:
   - Run: ${commands.checkFormat}
   - On failure: tell user to run ${commands.reformat} and re-stage

2. .git-hooks/pre-push — compilation warnings + tests (~3min), blocks push on failure:
   - Step 1: Clean compile and check for [warn] in output
     Run: ${commands.cleanCompile}
     Grep output for [warn], block if found
   - Step 2: Run full test suite
     Run: ${commands.test}

Both scripts should:
- Use #!/usr/bin/env bash with set -euo pipefail
- Print colored status messages (red for blocked, green for passed, yellow for in-progress)
- Show clear error box explaining what failed and how to fix it
- Mention --no-verify bypass option (but mark as not recommended)

Install hooks: git config core.hooksPath .git-hooks"""

  private def ciGuidance(commands: Commands, ciPlatform: String): String =
    val workflowStructure = ciPlatform match
      case "GitHub Actions" =>
        s"""Create .github/workflows/ci.yml triggered on pull_request to main.

Job structure — 3 parallel + 1 sequential:
  Parallel (run simultaneously for fast feedback):
    1. compile: ${commands.compile}
    2. format: ${commands.checkFormat}
    3. lint: ${commands.fixCheck}
  Sequential (after compile succeeds):
    4. test (needs: compile): ${commands.test}

Include:
- Concurrency group to cancel in-progress runs on new pushes
- Java 21 setup (temurin)
- Coursier and build tool caching
- Read permissions for contents and packages"""

      case "GitLab CI" =>
        s"""Create .gitlab-ci.yml with stages: [build, test, lint].

Jobs:
  build stage (parallel):
    - compile: ${commands.compile}
    - format: ${commands.checkFormat}
    - lint: ${commands.fixCheck}
  test stage (after build):
    - test: ${commands.test}

Include Java 21 image and dependency caching."""

      case _ =>
        s"""Set up CI with these checks:
  1. compile: ${commands.compile}
  2. format: ${commands.checkFormat}
  3. lint: ${commands.fixCheck}
  4. test (after compile): ${commands.test}"""

    s"""=== CI Workflow ===
$workflowStructure"""

  private def contributingGuidance(commands: Commands): String =
    s"""=== CONTRIBUTING.md ===
Create CONTRIBUTING.md covering these sections:

1. CI Checks — what the CI pipeline validates (compile, format, lint, test)
2. Git Hooks — how to install hooks (git config core.hooksPath .git-hooks),
   what pre-commit and pre-push check
3. Local Development — how to run checks locally:
   - Format check: ${commands.checkFormat}
   - Auto-format: ${commands.reformat}
   - Lint: ${commands.fixCheck}
   - Tests: ${commands.test}
4. Troubleshooting — common issues and fixes (e.g., hook not running,
   format failures after merge)"""
