// PURPOSE: Unit tests for fix prompt generation logic
// PURPOSE: Verifies prompt includes failed checks, build system, and CI platform

package iw.core.test

import iw.core.model.{BuildSystem, FixPrompt}

class FixPromptTest extends munit.FunSuite:
  test("prompt includes all failed check names"):
    val failures = List("Scalafmt", "Scalafix", "WartRemover")
    val prompt = FixPrompt.generate(failures, BuildSystem.Mill, "GitHub Actions")

    failures.foreach { checkName =>
      assert(prompt.contains(checkName), s"Prompt should contain '$checkName'")
    }

  test("prompt includes build system name"):
    val failures = List("Scalafmt")
    val prompt = FixPrompt.generate(failures, BuildSystem.Mill, "GitHub Actions")

    assert(prompt.contains("Mill"), "Prompt should contain build system name")

  test("prompt includes CI platform"):
    val failures = List("Scalafmt")
    val prompt = FixPrompt.generate(failures, BuildSystem.SBT, "GitLab CI")

    assert(prompt.contains("GitLab CI"), "Prompt should contain CI platform")

  test("prompt is non-empty for non-empty failures"):
    val failures = List("Scalafmt")
    val prompt = FixPrompt.generate(failures, BuildSystem.Mill, "GitHub Actions")

    assert(prompt.nonEmpty, "Prompt should not be empty when there are failures")

  test("empty failures returns empty prompt"):
    val failures = List.empty[String]
    val prompt = FixPrompt.generate(failures, BuildSystem.Mill, "GitHub Actions")

    assertEquals(prompt, "", "Prompt should be empty when there are no failures")

  test("prompt handles Unknown build system"):
    val failures = List("Scalafmt")
    val prompt = FixPrompt.generate(failures, BuildSystem.Unknown, "GitHub Actions")

    assert(prompt.contains("Unknown"), "Prompt should handle Unknown build system")

  test("prompt handles Unknown CI platform"):
    val failures = List("Scalafmt")
    val prompt = FixPrompt.generate(failures, BuildSystem.Mill, "Unknown")

    assert(prompt.contains("Unknown"), "Prompt should handle Unknown CI platform")

  test("prompt includes multiple failed checks"):
    val failures = List("Scalafmt", "Scalafix")
    val prompt = FixPrompt.generate(failures, BuildSystem.ScalaCli, "GitHub Actions")

    assertEquals(prompt.count(_ == '\n') > 5, true, "Prompt should be detailed with multiple lines")
