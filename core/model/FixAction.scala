// PURPOSE: Hook trait for doctor --fix remediation actions
// PURPOSE: Plugins provide FixAction implementations to handle quality gate failures

package iw.core.model

/** Context passed to fix action hooks when doctor --fix is invoked. */
case class DoctorFixContext(
    failedChecks: List[String],
    buildSystem: BuildSystem,
    ciPlatform: String,
    config: ProjectConfiguration
)

/** Hook trait for remediation behavior in doctor --fix.
  *
  * Implementations run an external tool to fix quality gate failures. Returns
  * exit code (0 = success).
  */
trait FixAction:
  def fix(ctx: DoctorFixContext): Int
