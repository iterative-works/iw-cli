// PURPOSE: Types for extensible environment validation checks
// PURPOSE: Provides CheckResult enum and Check case class for doctor checks

package iw.core

enum CheckResult:
  case Success(message: String)
  case Warning(message: String, hint: Option[String] = None)
  case Error(message: String, hint: Option[String] = None)
  case Skip(reason: String)

case class Check(name: String, run: ProjectConfiguration => CheckResult)

object DoctorChecks:
  /** Run all provided checks against the configuration */
  def runAll(checks: List[Check], config: ProjectConfiguration): List[(String, CheckResult)] =
    checks.map(c => (c.name, c.run(config)))
