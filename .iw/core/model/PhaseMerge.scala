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

  /** True for statuses that represent a terminal failure (Failed, Cancelled). */
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
  /** Set by the polling loop caller when CI does not finish within the timeout. */
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
    checks match
      case Nil => CIVerdict.NoChecksFound
      case _ =>
        val (failures, rest) = checks.partition(_.status.isTerminalFailure)
        if failures.nonEmpty then CIVerdict.SomeFailed(failures)
        else if rest.exists(_.status == CICheckStatus.Pending) then CIVerdict.StillRunning
        else CIVerdict.AllPassed

  /** Determine whether another recovery attempt should be made.
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

  /** Parse JSON output of `gh pr checks --json name,state,link,bucket` into CI check results.
    *
    * State mapping:
    * - "SUCCESS"   → CICheckStatus.Passed
    * - "FAILURE"   → CICheckStatus.Failed
    * - "PENDING"   → CICheckStatus.Pending
    * - "CANCELLED" → CICheckStatus.Cancelled
    * - anything else → CICheckStatus.Unknown
    *
    * An empty `link` field maps to `url = None`; a non-empty link maps to `url = Some(link)`.
    *
    * @param json Raw JSON string from `gh pr checks --json name,state,link,bucket`
    * @return Right(checks) on success, Left(errorMessage) on parse failure
    */
  def parseGhChecksJson(json: String): Either[String, List[CICheckResult]] =
    try
      import ujson.*
      val parsed = read(json)
      val arr =
        try parsed.arr
        catch case _: Exception => return Left(s"Expected a JSON array but got: ${parsed.getClass.getSimpleName}")
      val checks = arr.toList.map { item =>
        val name = item("name").str
        val state = item("state").str
        val link = item("link").str
        val status = state match
          case "SUCCESS"   => CICheckStatus.Passed
          case "FAILURE"   => CICheckStatus.Failed
          case "PENDING"   => CICheckStatus.Pending
          case "CANCELLED" => CICheckStatus.Cancelled
          case _           => CICheckStatus.Unknown
        val url = if link.isEmpty then None else Some(link)
        CICheckResult(name, status, url)
      }
      Right(checks)
    catch
      case e: Exception => Left(s"Failed to parse gh checks JSON: ${e.getMessage}")

  private val githubPrPattern = """https://github\.com/.+/pull/(\d+)""".r
  private val gitlabMrPattern = """https://.+/-/merge_requests/(\d+)""".r

  /** Extract the PR or MR number from a GitHub or GitLab URL.
    *
    * @param url A GitHub PR URL or GitLab MR URL (leading/trailing whitespace is stripped)
    * @return Right(number) on success, Left(errorMessage) for empty, blank, or unrecognised input
    */
  def extractPrNumber(url: String): Either[String, Int] =
    val trimmed = url.trim
    if trimmed.isEmpty then Left("URL must not be blank")
    else
      trimmed match
        case githubPrPattern(n) => Right(n.toInt)
        case gitlabMrPattern(n) => Right(n.toInt)
        case _                  => Left(s"Unrecognised PR/MR URL: $trimmed")
