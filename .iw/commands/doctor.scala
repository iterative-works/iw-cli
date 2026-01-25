// PURPOSE: Check system dependencies and configuration
// USAGE: iw doctor
// ARGS:
//   None
// EXAMPLE: iw doctor

import iw.core.model.*
import iw.core.adapters.*
import iw.core.output.*

val ConfigPath = os.pwd / Constants.Paths.IwDir / "config.conf"

// Base checks defined as immutable values
val baseChecks: List[Check] = List(
  Check("Git repository", { _ =>
    val currentDir = os.Path(System.getProperty(Constants.SystemProps.UserDir))
    if GitAdapter.isGitRepository(currentDir) then
      CheckResult.Success("Found")
    else
      CheckResult.Error("Not found", "Initialize git repository: git init")
  }),
  Check("Configuration", { _ =>
    ConfigFileRepository.read(ConfigPath) match
      case Some(_) =>
        CheckResult.Success(s"${Constants.Paths.ConfigFile} valid")
      case None =>
        CheckResult.Error("Missing or invalid", "Run: iw init")
  })
)

// Collect hook checks from discovered hook classes via reflection
private def collectHookChecks(): List[Check] =
  val hookClasses = sys.env.getOrElse(Constants.EnvVars.IwHookClasses, "")
  if hookClasses.isEmpty then Nil
  else hookClasses.split(",").toList.flatMap { className =>
    try
      val clazz = Class.forName(s"$className$$") // Scala object class names end with $
      val instance = clazz.getField(Constants.ScalaReflection.ModuleField).get(null)

      // Collect all fields of type Check from the hook
      clazz.getDeclaredMethods
        .filter(_.getReturnType == classOf[Check])
        .filter(_.getParameterCount == 0)
        .flatMap { method =>
          try Some(method.invoke(instance).asInstanceOf[Check])
          catch case _: Exception => None
        }
        .toList
    catch
      case _: Exception => Nil // Hook not present or error accessing checks
  }

@main def doctor(args: String*): Unit =
  // Load configuration (use default if not available for hook checks)
  val config = ConfigFileRepository.read(ConfigPath).getOrElse(
    ProjectConfiguration(IssueTrackerType.Linear, "UNKNOWN", "unknown")
  )

  System.out.println("Environment Check")
  System.out.println()

  // Collect all checks: base + hooks (immutable composition)
  val allChecks = baseChecks ++ collectHookChecks()

  // Run all checks
  val results = DoctorChecks.runAll(allChecks, config)

  var errorCount = 0
  var warningCount = 0

  // Display results
  results.foreach { case (name, result) =>
    result match
      case CheckResult.Success(message) =>
        System.out.println(f"  ✓ $name%-20s $message")

      case CheckResult.Warning(message) =>
        warningCount += 1
        System.out.println(f"  ⚠ $name%-20s $message")

      case CheckResult.WarningWithHint(message, hintText) =>
        warningCount += 1
        System.out.println(f"  ⚠ $name%-20s $message")
        System.out.println(s"    → $hintText")

      case CheckResult.Error(message, hintText) =>
        errorCount += 1
        System.out.println(f"  ✗ $name%-20s $message")
        System.out.println(s"    → $hintText")

      case CheckResult.Skip(reason) =>
        System.out.println(f"  - $name%-20s Skipped ($reason)")
  }

  System.out.println()

  // Summary
  if errorCount == 0 && warningCount == 0 then
    System.out.println("All checks passed")
    sys.exit(0)
  else if errorCount > 0 then
    val plural = if errorCount == 1 then "check" else "checks"
    System.out.println(s"$errorCount $plural failed")
    sys.exit(1)
  else
    val plural = if warningCount == 1 then "warning" else "warnings"
    System.out.println(s"$warningCount $plural")
    sys.exit(0)
