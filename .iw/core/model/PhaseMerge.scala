// PURPOSE: Pure decision functions for phase-merge CI check evaluation
// PURPOSE: Given CI check statuses, determines verdict (pass/fail/pending) without I/O

package iw.core.model

/** Status of a single CI check. */
enum CICheckStatus:
  case Passed
  case Failed
  case Pending
  case Cancelled
  case Unknown

/** A single CI check result with its name, status, and optional details URL. */
case class CICheckResult(
    name: String,
    status: CICheckStatus,
    url: Option[String] = None
)

/** Overall verdict after evaluating all CI checks for a PR. */
enum CIVerdict:
  case AllPassed
  case SomeFailed(failedChecks: List[CICheckResult])
  case StillRunning
  case NoChecksFound
  case TimedOut

/** Configuration for the phase-merge CI polling loop. */
case class PhaseMergeConfig(
    timeoutMs: Long = 1_800_000L,
    pollIntervalMs: Long = 30_000L,
    maxRetries: Int = 2
)

/** Pure decision logic for phase-merge CI check evaluation.
  *
  * Classifies CI check results into actionable verdicts without performing I/O.
  */
object PhaseMerge:

  /** Evaluate a list of CI check results and return the overall verdict.
    *
    * Precedence rules:
    * - Empty list → NoChecksFound
    * - Any Failed or Cancelled → SomeFailed (carries the failing checks)
    * - Any Pending (no failures) → StillRunning
    * - Otherwise (all Passed or Unknown) → AllPassed
    *
    * @param checks All CI check results for a PR
    * @return CIVerdict describing the overall CI state
    */
  def evaluateChecks(checks: List[CICheckResult]): CIVerdict =
    if checks.isEmpty then CIVerdict.NoChecksFound
    else
      val failed = checks.filter(c =>
        c.status == CICheckStatus.Failed || c.status == CICheckStatus.Cancelled
      )
      if failed.nonEmpty then CIVerdict.SomeFailed(failed)
      else if checks.exists(_.status == CICheckStatus.Pending) then CIVerdict.StillRunning
      else CIVerdict.AllPassed

  /** Return true if another polling attempt should be made.
    *
    * @param attempt Zero-based attempt index (0 = first attempt)
    * @param config  Merge configuration carrying the max retries limit
    * @return true when attempt is strictly less than maxRetries
    */
  def shouldRetry(attempt: Int, config: PhaseMergeConfig): Boolean =
    attempt < config.maxRetries

  /** Build a prompt describing which CI checks failed, for use in recovery workflows.
    *
    * @param failedChecks Checks whose status is Failed or Cancelled
    * @return Human-readable string listing each check's name, status, and URL (when present)
    */
  def buildRecoveryPrompt(failedChecks: List[CICheckResult]): String =
    val lines = failedChecks.map { check =>
      val urlPart = check.url.fold("")(u => s" ($u)")
      s"- ${check.name}: ${check.status}$urlPart"
    }
    s"The following CI checks failed:\n${lines.mkString("\n")}"
