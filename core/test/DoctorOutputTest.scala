// PURPOSE: Unit tests for DoctorOutput pure renderer (per-check, body, summary, full render)
// PURPOSE: Absorbs the formatting / grouping / exit-code scenarios from doctor.bats

package iw.core.test

import iw.core.model.CheckResult
import iw.core.output.DoctorOutput
import iw.core.output.DoctorOutput.Summary

class DoctorOutputTest extends munit.FunSuite:

  // ----- renderCheck -----

  test("renderCheck Success emits one check-mark line"):
    val lines = DoctorOutput.renderCheck("Git", CheckResult.Success("Found"))
    assertEquals(lines.size, 1)
    assert(lines.head.contains("✓"), s"missing ✓: ${lines.head}")
    assert(lines.head.contains("Git"))
    assert(lines.head.contains("Found"))

  test("renderCheck Warning emits one warning-symbol line"):
    val lines = DoctorOutput.renderCheck("Cache", CheckResult.Warning("Stale"))
    assertEquals(lines.size, 1)
    assert(lines.head.contains("⚠"))
    assert(lines.head.contains("Stale"))

  test("renderCheck WarningWithHint emits status line followed by hint line"):
    val lines = DoctorOutput.renderCheck(
      "Scalafix",
      CheckResult.WarningWithHint("Stale rules", "Run: scalafix --rules")
    )
    assertEquals(lines.size, 2)
    assert(lines(0).contains("⚠"))
    assert(lines(0).contains("Stale rules"))
    assert(lines(1).contains("→"))
    assert(lines(1).contains("Run: scalafix --rules"))

  test("renderCheck Error emits status line followed by hint line"):
    val lines = DoctorOutput.renderCheck(
      "Config",
      CheckResult.Error("Missing or invalid", "Run: iw init")
    )
    assertEquals(lines.size, 2)
    assert(lines(0).contains("✗"))
    assert(lines(0).contains("Missing or invalid"))
    assert(lines(1).contains("→"))
    assert(lines(1).contains("Run: iw init"))

  test("renderCheck Skip emits a single dash-prefixed line with the reason"):
    val lines =
      DoctorOutput.renderCheck("LinearToken", CheckResult.Skip("Not Linear"))
    assertEquals(lines.size, 1)
    assert(lines.head.contains("-"))
    assert(lines.head.contains("Skipped (Not Linear)"))

  // ----- Summary -----

  test("Summary.from counts Errors as errorCount"):
    val results = List(
      ("A", CheckResult.Success("ok"), "Environment"),
      ("B", CheckResult.Error("bad", "fix"), "Environment"),
      ("C", CheckResult.Error("worse", "fix2"), "Quality")
    )
    val s = Summary.from(results)
    assertEquals(s.errorCount, 2)
    assertEquals(s.warningCount, 0)

  test("Summary.from counts Warning and WarningWithHint as warningCount"):
    val results = List(
      ("A", CheckResult.Warning("w1"), "Environment"),
      ("B", CheckResult.WarningWithHint("w2", "h"), "Quality"),
      ("C", CheckResult.Success("ok"), "Environment")
    )
    val s = Summary.from(results)
    assertEquals(s.errorCount, 0)
    assertEquals(s.warningCount, 2)

  test("Summary.from does not count Skip toward warnings or errors"):
    val results = List(
      ("A", CheckResult.Skip("n/a"), "Environment"),
      ("B", CheckResult.Success("ok"), "Environment")
    )
    val s = Summary.from(results)
    assertEquals(s.errorCount, 0)
    assertEquals(s.warningCount, 0)

  test("Summary.exitCode is 1 when any error is present"):
    assertEquals(Summary(errorCount = 1, warningCount = 0).exitCode, 1)
    assertEquals(Summary(errorCount = 3, warningCount = 5).exitCode, 1)

  test("Summary.exitCode is 0 with only warnings"):
    assertEquals(Summary(errorCount = 0, warningCount = 2).exitCode, 0)

  test("Summary.exitCode is 0 when all checks pass"):
    assertEquals(Summary(errorCount = 0, warningCount = 0).exitCode, 0)

  test("Summary.line shows 'All checks passed' on clean run"):
    assertEquals(Summary(0, 0).line, "All checks passed")

  test("Summary.line uses singular 'check' for a single error"):
    assertEquals(Summary(1, 0).line, "1 check failed")

  test("Summary.line uses plural 'checks' for multiple errors"):
    assertEquals(Summary(3, 0).line, "3 checks failed")

  test("Summary.line uses singular 'warning' for a single warning"):
    assertEquals(Summary(0, 1).line, "1 warning")

  test("Summary.line uses plural 'warnings' for multiple warnings"):
    assertEquals(Summary(0, 2).line, "2 warnings")

  test("Summary.line prefers the error message when both present"):
    assertEquals(Summary(2, 3).line, "2 checks failed")

  // ----- renderBody -----

  test("renderBody with headers includes both section banners"):
    val results = List(
      ("Git", CheckResult.Success("Found"), "Environment"),
      ("Scalafmt", CheckResult.Success("OK"), "Quality")
    )
    val lines = DoctorOutput.renderBody(results, showHeaders = true)
    assert(lines.contains("  === Environment ==="))
    assert(lines.contains("  === Project Quality Gates ==="))

  test("renderBody without headers suppresses section banners"):
    val results = List(
      ("Git", CheckResult.Success("Found"), "Environment"),
      ("Scalafmt", CheckResult.Success("OK"), "Quality")
    )
    val lines = DoctorOutput.renderBody(results, showHeaders = false)
    assert(!lines.exists(_.contains("=== Environment ===")))
    assert(!lines.exists(_.contains("=== Project Quality Gates ===")))

  test("renderBody puts Environment checks before Quality checks"):
    // Note: provided in reverse to assert grouping order is independent of input order
    val results = List(
      ("Scalafmt", CheckResult.Success("OK"), "Quality"),
      ("Git", CheckResult.Success("Found"), "Environment")
    )
    val lines = DoctorOutput.renderBody(results, showHeaders = true)
    val envIdx = lines.indexWhere(_.contains("=== Environment ==="))
    val qualIdx = lines.indexWhere(_.contains("=== Project Quality Gates ==="))
    assert(envIdx >= 0 && qualIdx >= 0)
    assert(envIdx < qualIdx, s"env header at $envIdx, quality at $qualIdx")

  test(
    "renderBody preserves run order of checks within a category"
  ):
    val results = List(
      ("First", CheckResult.Success("a"), "Environment"),
      ("Second", CheckResult.Success("b"), "Environment"),
      ("Third", CheckResult.Success("c"), "Environment")
    )
    val lines = DoctorOutput.renderBody(results, showHeaders = false)
    val firstIdx = lines.indexWhere(_.contains("First"))
    val secondIdx = lines.indexWhere(_.contains("Second"))
    val thirdIdx = lines.indexWhere(_.contains("Third"))
    assert(firstIdx < secondIdx, s"$firstIdx vs $secondIdx")
    assert(secondIdx < thirdIdx, s"$secondIdx vs $thirdIdx")

  test("renderBody skips a category with no results"):
    val results = List(
      ("Git", CheckResult.Success("Found"), "Environment")
    )
    val lines = DoctorOutput.renderBody(results, showHeaders = true)
    assert(lines.contains("  === Environment ==="))
    assert(!lines.contains("  === Project Quality Gates ==="))

  // ----- render (full) -----

  test("render starts with the 'Environment Check' header"):
    val rendered = DoctorOutput.render(Nil, showHeaders = true)
    assertEquals(rendered.lines.headOption, Some("Environment Check"))

  test("render ends with the summary line"):
    val results = List(
      ("Git", CheckResult.Error("Not found", "git init"), "Environment")
    )
    val rendered = DoctorOutput.render(results, showHeaders = true)
    assertEquals(rendered.lines.lastOption, Some("1 check failed"))

  test("render reports exit code 1 when any check errors"):
    val results = List(
      ("Git", CheckResult.Success("ok"), "Environment"),
      ("Config", CheckResult.Error("Missing", "Run init"), "Environment")
    )
    val rendered = DoctorOutput.render(results, showHeaders = true)
    assertEquals(rendered.exitCode, 1)

  test("render reports exit code 0 when only warnings present"):
    val results = List(
      ("Cache", CheckResult.Warning("stale"), "Environment")
    )
    val rendered = DoctorOutput.render(results, showHeaders = true)
    assertEquals(rendered.exitCode, 0)
