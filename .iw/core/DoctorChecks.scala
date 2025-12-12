// PURPOSE: Registry for extensible environment validation checks
// PURPOSE: Provides CheckResult enum and DoctorChecks singleton for check registration

package iw.core

enum CheckResult:
  case Success(message: String)
  case Warning(message: String, hint: Option[String] = None)
  case Error(message: String, hint: Option[String] = None)
  case Skip(reason: String)

case class Check(name: String, run: ProjectConfiguration => CheckResult)

class DoctorChecksRegistry:
  private var registry: List[Check] = Nil

  def register(name: String)(check: ProjectConfiguration => CheckResult): Unit =
    registry = Check(name, check) :: registry

  def all: List[Check] = registry.reverse

  def runAll(config: ProjectConfiguration): List[(String, CheckResult)] =
    registry.reverse.map(c => (c.name, c.run(config)))

// Global singleton instance for the application
object DoctorChecks extends DoctorChecksRegistry
