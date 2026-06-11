// PURPOSE: Pure renderer for `iw doctor` output (check lines, category sections, summary, exit code)
// PURPOSE: Lets the command script focus on I/O while the wiring is unit-tested at the line level

package iw.core.output

import iw.core.model.CheckResult

object DoctorOutput:

  /** Aggregate counts derived from a result list, plus the human-readable
    * summary line and the corresponding exit code.
    */
  final case class Summary(errorCount: Int, warningCount: Int):
    def exitCode: Int = if errorCount > 0 then 1 else 0

    def line: String =
      if errorCount == 0 && warningCount == 0 then "All checks passed"
      else if errorCount > 0 then
        val plural = if errorCount == 1 then "check" else "checks"
        s"$errorCount $plural failed"
      else
        val plural = if warningCount == 1 then "warning" else "warnings"
        s"$warningCount $plural"

  object Summary:
    def from(results: List[(String, CheckResult, String)]): Summary =
      val errors = results.count(_._2.isInstanceOf[CheckResult.Error])
      val warnings = results.count { case (_, r, _) =>
        r.isInstanceOf[CheckResult.Warning] ||
        r.isInstanceOf[CheckResult.WarningWithHint]
      }
      Summary(errors, warnings)

  /** Render a single check result. Errors and warnings-with-hint produce two
    * lines (the status line + the indented hint); other variants produce one.
    */
  def renderCheck(name: String, result: CheckResult): List[String] =
    result match
      case CheckResult.Success(message) =>
        List(f"  ✓ $name%-20s $message")
      case CheckResult.Warning(message) =>
        List(f"  ⚠ $name%-20s $message")
      case CheckResult.WarningWithHint(message, hint) =>
        List(f"  ⚠ $name%-20s $message", s"    → $hint")
      case CheckResult.Error(message, hint) =>
        List(f"  ✗ $name%-20s $message", s"    → $hint")
      case CheckResult.Skip(reason) =>
        List(f"  - $name%-20s Skipped ($reason)")

  /** Category order rendered by `iw doctor`. Other categories from plugin hooks
    * would be ignored (currently no other categories exist).
    */
  val CategoryOrder: List[String] = List("Environment", "Quality")

  /** Section header lines (e.g. `  === Environment ===`). */
  val CategoryHeaders: Map[String, String] = Map(
    "Environment" -> "  === Environment ===",
    "Quality" -> "  === Project Quality Gates ==="
  )

  /** Render the check body in canonical category order.
    *
    * @param results
    *   `(checkName, result, category)` tuples in run order
    * @param showHeaders
    *   include `=== Environment ===` style headers and surrounding blank lines.
    *   When `iw doctor --env` / `--quality` filters to a single category, the
    *   original command suppresses the header.
    */
  def renderBody(
      results: List[(String, CheckResult, String)],
      showHeaders: Boolean
  ): List[String] =
    val grouped = results.groupBy(_._3)
    CategoryOrder.flatMap { category =>
      grouped.get(category).filter(_.nonEmpty) match
        case None       => List.empty
        case Some(rows) =>
          val header =
            if showHeaders then List("", CategoryHeaders(category), "")
            else List.empty
          val checkLines = rows.flatMap { case (name, result, _) =>
            renderCheck(name, result)
          }
          header ++ checkLines
    }

  /** Complete output (lines + exit code) for a non-fix-mode `iw doctor` run.
    * Fix-mode handling stays in the command script because it requires
    * dispatching to a FixAction hook (side-effecting).
    */
  final case class Rendered(lines: List[String], exitCode: Int):
    def text: String = lines.mkString("\n")

  def render(
      results: List[(String, CheckResult, String)],
      showHeaders: Boolean
  ): Rendered =
    val summary = Summary.from(results)
    val lines = List("Environment Check", "") ++
      renderBody(results, showHeaders) ++
      List("", summary.line)
    Rendered(lines, summary.exitCode)
