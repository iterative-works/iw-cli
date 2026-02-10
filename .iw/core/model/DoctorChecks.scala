// PURPOSE: Types for extensible environment validation checks
// PURPOSE: Provides CheckResult enum and Check case class for doctor checks

package iw.core.model

enum CheckResult:
  case Success(message: String)
  case Warning(message: String)
  case WarningWithHint(message: String, hintText: String)
  case Error(message: String, hintText: String)
  case Skip(reason: String)

  def hint: Option[String] = this match
    case Success(_) => None
    case Warning(_) => None
    case WarningWithHint(_, h) => Some(h)
    case Error(_, h) => Some(h)
    case Skip(_) => None

case class Check(name: String, run: ProjectConfiguration => CheckResult)

object DoctorChecks:
  /** Run all provided checks against the configuration */
  def runAll(checks: List[Check], config: ProjectConfiguration): List[(String, CheckResult)] =
    checks.map(c => (c.name, c.run(config)))
