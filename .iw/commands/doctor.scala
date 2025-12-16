// PURPOSE: Check system dependencies and configuration
// USAGE: iw doctor
// ARGS:
//   None
// EXAMPLE: iw doctor

import iw.core.*
import java.nio.file.{Path, Paths}

val ConfigPath = Paths.get(".iw/config.conf")

// Base checks defined as immutable values
val baseChecks: List[Check] = List(
  Check("Git repository", { _ =>
    val currentDir = Paths.get(System.getProperty("user.dir"))
    if GitAdapter.isGitRepository(currentDir) then
      CheckResult.Success("Found")
    else
      CheckResult.Error("Not found", Some("Initialize git repository: git init"))
  }),
  Check("Configuration", { _ =>
    ConfigFileRepository.read(ConfigPath) match
      case Some(_) =>
        CheckResult.Success(".iw/config.conf valid")
      case None =>
        CheckResult.Error("Missing or invalid", Some("Run: iw init"))
  })
)

// Collect hook checks from discovered hook classes via reflection
// IW_HOOK_CLASSES env var contains comma-separated list of hook class names
private def collectHookChecks(): List[Check] =
  val hookClasses = sys.env.getOrElse("IW_HOOK_CLASSES", "")
  if hookClasses.isEmpty then Nil
  else hookClasses.split(",").toList.flatMap { className =>
    try
      val clazz = Class.forName(s"$className$$") // Scala object class names end with $
      val instance = clazz.getField("MODULE$").get(null)
      val checkField = clazz.getMethod("check")
      Some(checkField.invoke(instance).asInstanceOf[Check])
    catch
      case _: Exception => None // Hook not present or doesn't have check field
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

      case CheckResult.Warning(message, hint) =>
        warningCount += 1
        System.out.println(f"  ⚠ $name%-20s $message")
        hint.foreach(h => System.out.println(s"    → $h"))

      case CheckResult.Error(message, hint) =>
        errorCount += 1
        System.out.println(f"  ✗ $name%-20s $message")
        hint.foreach(h => System.out.println(s"    → $h"))

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
