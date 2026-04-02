// PURPOSE: Pure decision functions for phase-merge CI check evaluation
// PURPOSE: Given CI check statuses and PR URLs, determines verdict and extracts PR numbers without I/O

package iw.core.model

/** Status of a single CI check. */
enum CICheckStatus:
  case Passed
  case Failed
  case Pending
  case Cancelled
  case Unknown

  /** True for statuses that represent a terminal failure (Failed, Cancelled).
    */
  def isTerminalFailure: Boolean =
    this == Failed || this == Cancelled

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

  /** Set by the polling loop caller when CI does not finish within the timeout.
    */
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
    *   - Empty list → NoChecksFound
    *   - Any Failed or Cancelled → SomeFailed (carries the failing checks)
    *   - Any Pending (no failures) → StillRunning
    *   - Otherwise (all Passed or Unknown) → AllPassed
    *
    * @param checks
    *   All CI check results for a PR
    * @return
    *   CIVerdict describing the overall CI state
    */
  def evaluateChecks(checks: List[CICheckResult]): CIVerdict =
    checks match
      case Nil => CIVerdict.NoChecksFound
      case _   =>
        val (failures, rest) = checks.partition(_.status.isTerminalFailure)
        if failures.nonEmpty then CIVerdict.SomeFailed(failures)
        else if rest.exists(_.status == CICheckStatus.Pending) then
          CIVerdict.StillRunning
        else CIVerdict.AllPassed

  /** Determine whether another recovery attempt should be made.
    *
    * @param attempt
    *   Zero-based attempt index (0 = first attempt)
    * @param config
    *   Merge configuration carrying the max retries limit
    * @return
    *   true when attempt is strictly less than maxRetries
    */
  def shouldRetry(attempt: Int, config: PhaseMergeConfig): Boolean =
    attempt < config.maxRetries

  /** Build a prompt describing which CI checks failed, for use in recovery
    * workflows.
    *
    * @param failedChecks
    *   Checks whose status is Failed or Cancelled
    * @return
    *   Human-readable string listing each check's name, status, and URL (when
    *   present)
    */
  def buildRecoveryPrompt(failedChecks: List[CICheckResult]): String =
    val lines = failedChecks.map { check =>
      val urlPart = check.url.fold("")(u => s" ($u)")
      s"- ${check.name}: ${check.status}$urlPart"
    }
    s"The following CI checks failed:\n${lines.mkString("\n")}"

  /** Parse a human-readable duration string into milliseconds.
    *
    * Accepts the following formats:
    *   - `"Ns"` — N seconds (e.g., `"30s"` → 30,000 ms)
    *   - `"Nm"` — N minutes (e.g., `"5m"` → 300,000 ms)
    *   - `"Nh"` — N hours (e.g., `"2h"` → 7,200,000 ms)
    *
    * @param input
    *   Duration string (e.g., `"30s"`, `"5m"`, `"2h"`)
    * @return
    *   Right(milliseconds) on success, Left(errorMessage) for invalid input
    */
  def parseDuration(input: String): Either[String, Long] =
    if input.isEmpty then Left("Invalid duration: must not be empty")
    else
      val suffix = input.last
      val numberPart = input.dropRight(1)
      suffix match
        case 's' | 'm' | 'h' =>
          numberPart.toLongOption match
            case None =>
              Left(
                s"Invalid duration: '$input' — numeric value required before suffix"
              )
            case Some(n) if n < 0 =>
              Left(s"Invalid duration: '$input' — value must not be negative")
            case Some(n) =>
              val ms = suffix match
                case 's' => n * 1_000L
                case 'm' => n * 60_000L
                case 'h' => n * 3_600_000L
              Right(ms)
        case _ =>
          Left(
            s"Invalid duration: '$input' — unknown suffix '$suffix', expected s, m, or h"
          )

  /** Format a millisecond duration as a human-readable string.
    *
    * Picks the most natural unit:
    *   - hours if divisible by 3,600,000
    *   - minutes if divisible by 60,000
    *   - otherwise seconds
    *
    * @param ms
    *   Duration in milliseconds
    * @return
    *   Human-readable string (e.g., `"30m"`, `"45s"`, `"2h"`)
    */
  def formatDuration(ms: Long): String =
    if ms >= 3_600_000L && ms % 3_600_000L == 0 then s"${ms / 3_600_000L}h"
    else if ms >= 60_000L && ms % 60_000L == 0 then s"${ms / 60_000L}m"
    else s"${ms / 1_000L}s"

  private val githubPrPattern = """https://github\.com/.+/pull/(\d+)""".r
  private val gitlabMrPattern = """https://.+/-/merge_requests/(\d+)""".r

  /** Extract the PR or MR number from a GitHub or GitLab URL.
    *
    * @param url
    *   A GitHub PR URL or GitLab MR URL (leading/trailing whitespace is
    *   stripped)
    * @return
    *   Right(number) on success, Left(errorMessage) for empty, blank, or
    *   unrecognised input
    */
  def extractPrNumber(url: String): Either[String, Int] =
    val trimmed = url.trim
    if trimmed.isEmpty then Left("URL must not be blank")
    else
      trimmed match
        case githubPrPattern(n) => Right(n.toInt)
        case gitlabMrPattern(n) => Right(n.toInt)
        case _                  => Left(s"Unrecognised PR/MR URL: $trimmed")
