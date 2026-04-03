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
  Check(
    "Git repository",
    { _ =>
      val currentDir =
        os.Path(System.getProperty(Constants.SystemProps.UserDir))
      if GitAdapter.isGitRepository(currentDir) then
        CheckResult.Success("Found")
      else CheckResult.Error("Not found", "Initialize git repository: git init")
    }
  ),
  Check(
    "Configuration",
    { _ =>
      ConfigFileRepository.read(ConfigPath) match
        case Some(_) =>
          CheckResult.Success(s"${Constants.Paths.ConfigFile} valid")
        case None =>
          CheckResult.Error("Missing or invalid", "Run: iw init")
    }
  )
)

@main def doctor(args: String*): Unit =
  // Load configuration (use default if not available for hook checks)
  val config = ConfigFileRepository
    .read(ConfigPath)
    .getOrElse(
      ProjectConfiguration.create(IssueTrackerType.Linear, "UNKNOWN", "unknown")
    )

  // Parse flags
  val fixMode = args.contains("--fix")
  val filterCategory =
    if args.contains("--quality") || fixMode then Some("Quality")
    else if args.contains("--env") then Some("Environment")
    else None

  System.out.println("Environment Check")
  System.out.println()

  // Collect all checks: base + hooks (immutable composition)
  val allChecks = baseChecks ++ HookDiscovery.collectValues[Check]

  // Filter checks by category if requested
  val checksToRun = DoctorChecks.filterByCategory(allChecks, filterCategory)

  // Run all checks
  val results = DoctorChecks.runAll(checksToRun, config)

  // Group results by category
  val groupedResults = results.groupBy(_._3)

  // Display results grouped by category
  val categoryOrder = List("Environment", "Quality")
  val categoryHeaders = Map(
    "Environment" -> "  === Environment ===",
    "Quality" -> "  === Project Quality Gates ==="
  )

  categoryOrder.foreach { category =>
    groupedResults.get(category).foreach { categoryResults =>
      if categoryResults.nonEmpty then
        if filterCategory.isEmpty then
          System.out.println()
          System.out.println(categoryHeaders(category))
          System.out.println()

        categoryResults.foreach { case (name, result, _) =>
          result match
            case CheckResult.Success(message) =>
              System.out.println(f"  ✓ $name%-20s $message")

            case CheckResult.Warning(message) =>
              System.out.println(f"  ⚠ $name%-20s $message")

            case CheckResult.WarningWithHint(message, hintText) =>
              System.out.println(f"  ⚠ $name%-20s $message")
              System.out.println(s"    → $hintText")

            case CheckResult.Error(message, hintText) =>
              System.out.println(f"  ✗ $name%-20s $message")
              System.out.println(s"    → $hintText")

            case CheckResult.Skip(reason) =>
              System.out.println(f"  - $name%-20s Skipped ($reason)")
        }
    }
  }

  System.out.println()

  val errorCount = results.count { case (_, result, _) =>
    result.isInstanceOf[CheckResult.Error]
  }
  val warningCount = results.count { case (_, result, _) =>
    result.isInstanceOf[CheckResult.Warning] || result
      .isInstanceOf[CheckResult.WarningWithHint]
  }

  // Fix mode: invoke FixAction hook
  if fixMode then
    if errorCount == 0 then
      System.out.println("All quality gate checks pass. Nothing to fix.")
      sys.exit(0)
    else
      val fixActions = HookDiscovery.collectValues[FixAction]
      if fixActions.isEmpty then
        Output.warning(
          "No fix provider installed. Install a plugin that provides a FixAction hook."
        )
        sys.exit(1)
      else
        // Collect failed check names
        val failedChecks = results.collect {
          case (name, CheckResult.Error(_, _), _) => name
        }

        // Detect build system
        val buildSystem = BuildSystem.detect()

        // Detect CI platform from tracker type
        val ciPlatform = config.tracker.trackerType match
          case IssueTrackerType.GitHub => "GitHub Actions"
          case IssueTrackerType.GitLab => "GitLab CI"
          case _                       => "Unknown"

        val ctx =
          DoctorFixContext(failedChecks, buildSystem, ciPlatform, config)
        val exitCode = fixActions.head.fix(ctx)
        sys.exit(exitCode)

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
