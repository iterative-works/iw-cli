// PURPOSE: Hook trait for phase-merge CI failure recovery actions
// PURPOSE: Plugins provide RecoveryAction implementations to fix CI failures automatically

package iw.core.model

/** Context passed to recovery action hooks when CI checks fail during
  * phase-merge.
  */
case class RecoveryContext(
    failedChecks: List[CICheckResult],
    prUrl: String,
    branch: String,
    attempt: Int,
    maxRetries: Int
)

/** Hook trait for CI failure recovery in phase-merge.
  *
  * Implementations run an external tool to fix CI failures. Returns exit code
  * (0 = success).
  */
trait RecoveryAction:
  def recover(ctx: RecoveryContext): Int
