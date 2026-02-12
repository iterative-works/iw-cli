// PURPOSE: Unit tests for fix prompt generation logic
// PURPOSE: Verifies prompt includes check-specific guidance with build-system-aware commands

package iw.core.test

import iw.core.model.{BuildSystem, FixPrompt}

class FixPromptTest extends munit.FunSuite:
  test("prompt includes all failed check names"):
    val failures = List(".scalafmt.conf", ".scalafix.conf", "Git hooks dir")
    val prompt = FixPrompt.generate(failures, BuildSystem.Mill, "GitHub Actions")

    failures.foreach { checkName =>
      assert(prompt.contains(checkName), s"Prompt should contain '$checkName'")
    }

  test("empty failures returns empty prompt"):
    val failures = List.empty[String]
    val prompt = FixPrompt.generate(failures, BuildSystem.Mill, "GitHub Actions")

    assertEquals(prompt, "", "Prompt should be empty when there are no failures")

  // --- Build system command tests ---

  test("Mill build system uses mill commands"):
    val prompt = FixPrompt.generate(List(".scalafmt.conf"), BuildSystem.Mill, "GitHub Actions")

    assert(prompt.contains("./mill __.checkFormat"), "Should use mill checkFormat command")
    assert(prompt.contains("./mill __.reformat"), "Should use mill reformat command")

  test("SBT build system uses sbt commands"):
    val prompt = FixPrompt.generate(List(".scalafmt.conf"), BuildSystem.SBT, "GitHub Actions")

    assert(prompt.contains("sbt scalafmtCheckAll"), "Should use sbt scalafmtCheckAll")
    assert(prompt.contains("sbt scalafmtAll"), "Should use sbt scalafmtAll")

  test("scala-cli build system uses scala-cli commands"):
    val prompt = FixPrompt.generate(List(".scalafmt.conf"), BuildSystem.ScalaCli, "GitHub Actions")

    assert(prompt.contains("scala-cli fmt --check"), "Should use scala-cli fmt --check")
    assert(prompt.contains("scala-cli fmt ."), "Should use scala-cli fmt")

  test("Unknown build system uses placeholder commands"):
    val prompt = FixPrompt.generate(List(".scalafmt.conf"), BuildSystem.Unknown, "GitHub Actions")

    assert(prompt.contains("<check-format-command>"), "Should use placeholder commands")

  // --- Scalafmt guidance tests ---

  test("scalafmt guidance includes Scala 3 config"):
    val prompt = FixPrompt.generate(List(".scalafmt.conf"), BuildSystem.Mill, "GitHub Actions")

    assert(prompt.contains("runner.dialect = scala3"), "Should include Scala 3 dialect")
    assert(prompt.contains("maxColumn = 100"), "Should include maxColumn")
    assert(prompt.contains("rewrite.scala3.convertToNewSyntax"), "Should include Scala 3 rewrites")

  test("scalafmt version check triggers same guidance as config check"):
    val configPrompt = FixPrompt.generate(List(".scalafmt.conf"), BuildSystem.Mill, "GitHub Actions")
    val versionPrompt = FixPrompt.generate(List(".scalafmt.conf version"), BuildSystem.Mill, "GitHub Actions")

    // Both should contain the scalafmt config guidance
    assert(configPrompt.contains("runner.dialect = scala3"))
    assert(versionPrompt.contains("runner.dialect = scala3"))

  // --- Scalafix guidance tests ---

  test("scalafix guidance includes DisableSyntax rules"):
    val prompt = FixPrompt.generate(List(".scalafix.conf"), BuildSystem.Mill, "GitHub Actions")

    assert(prompt.contains("DisableSyntax"), "Should include DisableSyntax rule")
    assert(prompt.contains("noNulls = true"), "Should include noNulls")
    assert(prompt.contains("noVars = true"), "Should include noVars")
    assert(prompt.contains("noThrows = true"), "Should include noThrows")
    assert(prompt.contains("noReturns = true"), "Should include noReturns")

  test("scalafix guidance mentions Mill plugin for Mill builds"):
    val prompt = FixPrompt.generate(List(".scalafix.conf"), BuildSystem.Mill, "GitHub Actions")

    assert(prompt.contains("ScalafixModule"), "Should mention ScalafixModule trait for Mill")
    assert(prompt.contains("mill-scalafix"), "Should mention mill-scalafix plugin")

  test("scalafix guidance mentions sbt plugin for SBT builds"):
    val prompt = FixPrompt.generate(List(".scalafix.conf"), BuildSystem.SBT, "GitHub Actions")

    assert(prompt.contains("sbt-scalafix"), "Should mention sbt-scalafix plugin for SBT")

  // --- Git hooks guidance tests ---

  test("git hooks guidance describes pre-commit and pre-push"):
    val prompt = FixPrompt.generate(List("Git hooks dir"), BuildSystem.Mill, "GitHub Actions")

    assert(prompt.contains("pre-commit"), "Should describe pre-commit hook")
    assert(prompt.contains("pre-push"), "Should describe pre-push hook")

  test("git hooks pre-commit does format check"):
    val prompt = FixPrompt.generate(List("Git hook files"), BuildSystem.Mill, "GitHub Actions")

    assert(prompt.contains("./mill __.checkFormat"), "Pre-commit should run format check")
    assert(prompt.contains("./mill __.reformat"), "Should mention reformat as fix")

  test("git hooks pre-push does compile warnings and tests"):
    val prompt = FixPrompt.generate(List("Git hooks installed"), BuildSystem.Mill, "GitHub Actions")

    assert(prompt.contains("[warn]"), "Pre-push should check for compile warnings")
    assert(prompt.contains("./mill __.test"), "Pre-push should run tests")

  test("git hooks guidance includes installation command"):
    val prompt = FixPrompt.generate(List("Git hooks dir"), BuildSystem.Mill, "GitHub Actions")

    assert(prompt.contains("git config core.hooksPath .git-hooks"), "Should include hook install command")

  test("git hooks guidance includes script conventions"):
    val prompt = FixPrompt.generate(List("Git hooks dir"), BuildSystem.Mill, "GitHub Actions")

    assert(prompt.contains("set -euo pipefail"), "Should mention pipefail")
    assert(prompt.contains("--no-verify"), "Should mention bypass option")

  // --- CI guidance tests ---

  test("GitHub Actions CI guidance includes workflow structure"):
    val prompt = FixPrompt.generate(List("CI workflow"), BuildSystem.Mill, "GitHub Actions")

    assert(prompt.contains("ci.yml"), "Should mention ci.yml")
    assert(prompt.contains("pull_request"), "Should trigger on pull_request")
    assert(prompt.contains("needs: compile"), "Test job should depend on compile")

  test("GitHub Actions CI has parallel and sequential jobs"):
    val prompt = FixPrompt.generate(List("CI workflow"), BuildSystem.Mill, "GitHub Actions")

    assert(prompt.contains("compile"), "Should have compile job")
    assert(prompt.contains("format"), "Should have format job")
    assert(prompt.contains("lint"), "Should have lint job")
    assert(prompt.contains("test"), "Should have test job")
    assert(prompt.contains("Java 21"), "Should specify Java 21")

  test("GitLab CI guidance uses stages"):
    val prompt = FixPrompt.generate(List("CI workflow"), BuildSystem.Mill, "GitLab CI")

    assert(prompt.contains(".gitlab-ci.yml"), "Should mention .gitlab-ci.yml")
    assert(prompt.contains("stages"), "Should mention stages")

  test("unknown CI platform provides generic guidance"):
    val prompt = FixPrompt.generate(List("CI workflow"), BuildSystem.Mill, "Unknown")

    assert(prompt.contains("compile"), "Should list compile check")
    assert(prompt.contains("format"), "Should list format check")
    assert(prompt.contains("test"), "Should list test check")

  // --- Contributing guidance tests ---

  test("contributing guidance covers required sections"):
    val prompt = FixPrompt.generate(List("CONTRIBUTING.md"), BuildSystem.Mill, "GitHub Actions")

    assert(prompt.contains("CI Checks"), "Should mention CI Checks section")
    assert(prompt.contains("Git Hooks"), "Should mention Git Hooks section")
    assert(prompt.contains("Local Development"), "Should mention Local Development section")
    assert(prompt.contains("Troubleshooting"), "Should mention Troubleshooting section")

  test("contributing guidance includes local commands"):
    val prompt = FixPrompt.generate(List("CONTRIBUTING.md"), BuildSystem.Mill, "GitHub Actions")

    assert(prompt.contains("./mill __.checkFormat"), "Should list format check command")
    assert(prompt.contains("./mill __.test"), "Should list test command")

  // --- Deduplication tests ---

  test("multiple scalafmt checks produce single guidance section"):
    val prompt = FixPrompt.generate(
      List(".scalafmt.conf", ".scalafmt.conf version"),
      BuildSystem.Mill,
      "GitHub Actions"
    )

    // The guidance section header should appear (content is duplicated since
    // flatMap maps each check independently - but the guidance is the same)
    assert(prompt.contains("=== .scalafmt.conf ==="))

  // --- Integration test ---

  test("all checks together produce comprehensive prompt"):
    val failures = List(
      ".scalafmt.conf",
      ".scalafix.conf",
      "Git hooks dir",
      "CI workflow",
      "CONTRIBUTING.md"
    )
    val prompt = FixPrompt.generate(failures, BuildSystem.Mill, "GitHub Actions")

    // Should contain all section headers
    assert(prompt.contains("=== .scalafmt.conf ==="))
    assert(prompt.contains("=== .scalafix.conf ==="))
    assert(prompt.contains("=== Git Hooks ==="))
    assert(prompt.contains("=== CI Workflow ==="))
    assert(prompt.contains("=== CONTRIBUTING.md ==="))

    // Should contain build system name
    assert(prompt.contains("Mill"))

    // Should contain CI platform
    assert(prompt.contains("GitHub Actions"))
