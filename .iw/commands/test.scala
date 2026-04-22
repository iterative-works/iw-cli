// PURPOSE: Run project tests (unit tests, command compilation, and E2E tests)
// USAGE: iw test [unit|compile|e2e]
// ARGS:
//   [type]: Optional test type - 'unit' for Scala tests, 'compile' for command compilation, 'e2e' for BATS tests
//           If not provided, runs all tests including command compilation
// EXAMPLE: iw test
// EXAMPLE: iw test unit
// EXAMPLE: iw test compile
// EXAMPLE: iw test e2e

import iw.core.model.*
import iw.core.adapters.*
import iw.core.output.*

@main def test(args: String*): Unit =
  val testType = args.headOption.getOrElse("all")

  testType match
    case "--help" | "-h" =>
      showUsage()
      sys.exit(0)
    case "unit" =>
      val result = runUnitTests()
      sys.exit(if result then 0 else 1)
    case "compile" =>
      val result = runCommandCompileCheck()
      sys.exit(if result then 0 else 1)
    case "e2e" =>
      val result = runE2ETests()
      sys.exit(if result then 0 else 1)
    case "all" =>
      val unitResult = runUnitTests()
      val compileResult = runCommandCompileCheck()
      val e2eResult = runE2ETests()
      sys.exit(if unitResult && compileResult && e2eResult then 0 else 1)
    case other =>
      Output.error(s"Unknown test type: $other")
      showUsage()
      sys.exit(1)

def showUsage(): Unit =
  System.out.println("Usage: iw test [unit|compile|e2e]")
  System.out.println()
  System.out.println("Arguments:")
  System.out.println("  unit     Run Scala unit tests only")
  System.out.println("  compile  Check that all commands compile")
  System.out.println("  e2e      Run BATS E2E tests only")
  System.out.println("  (none)   Run all tests including command compilation")
  System.out.println()
  System.out.println("Examples:")
  System.out.println("  iw test           # Run all tests")
  System.out.println("  iw test unit      # Run only unit tests")
  System.out.println("  iw test compile   # Check command compilation only")
  System.out.println("  iw test e2e       # Run only E2E tests")

def runUnitTests(): Boolean =
  val installDir = os.Path(System.getenv("IW_INSTALL_DIR"))
  val testDir = installDir / "core" / "test"
  val coreDir = installDir / "core"

  if !os.exists(testDir) then
    Output.info("No unit tests found (missing core/test/)")
    true
  else
    val testFiles = os.list(testDir).filter(_.ext == "scala")
    if testFiles.isEmpty then
      Output.info("No unit test files found")
      true
    else
      Output.section("Running Unit Tests")

      // Pass entire core directory to scala-cli to include subdirectories like presentation/views
      // Use streaming to show output in real-time
      val command = Seq("scala-cli", "test", coreDir.toString)
      val coreExitCode = ProcessAdapter.runStreaming(command)

      Output.section("Running Dashboard Unit Tests")
      val millCommand = Seq((installDir / "mill").toString, "dashboard.test")
      val dashboardExitCode = ProcessAdapter.runStreaming(millCommand)

      coreExitCode == 0 && dashboardExitCode == 0

def runCommandCompileCheck(): Boolean =
  val installDir = os.Path(System.getenv("IW_INSTALL_DIR"))
  val commandsDir = installDir / "commands"
  val coreDir = installDir / "core"

  if !os.exists(commandsDir) then
    Output.info("No commands directory found (missing commands/)")
    true
  else
    val commandFiles = os.list(commandsDir).filter(_.ext == "scala")
    if commandFiles.isEmpty then
      Output.info("No command files found")
      true
    else
      Output.section("Checking Command Compilation")
      Output.info(s"Compiling ${commandFiles.length} commands...")

      // Dashboard bridge commands import iw.dashboard.* which lives outside core/
      val dashboardSrcDir = installDir / "dashboard" / "jvm" / "src"
      val dashboardBridgeCommands = Set("dashboard.scala", "server-daemon.scala")

      val results = commandFiles.sorted.map { commandFile =>
        val commandName = commandFile.last
        // Dashboard bridge commands need dashboard sources in addition to core
        val extraSources =
          if dashboardBridgeCommands.contains(commandName) && os.exists(dashboardSrcDir)
          then Seq(dashboardSrcDir.toString)
          else Seq.empty
        // Compile each command with the core module (suppress output unless there's an error)
        val result = ProcessAdapter.run(
          Seq("scala-cli", "compile", coreDir.toString) ++ extraSources ++ Seq(commandFile.toString, "--quiet")
        )

        if result.exitCode == 0 then
          Output.info(s"  ✓ $commandName")
          true
        else
          Output.error(s"  ✗ $commandName - compilation failed")
          // Show the error output
          if result.stderr.nonEmpty then
            System.err.println(result.stderr)
          false
      }

      val successCount = results.count(identity)
      val failCount = results.count(!_)
      val allSuccess = failCount == 0

      Output.info("")
      if allSuccess then
        Output.success(s"All $successCount commands compiled successfully")
      else
        Output.error(s"$failCount of ${commandFiles.length} commands failed to compile")

      allSuccess

def runE2ETests(): Boolean =
  val installDir = os.Path(System.getenv("IW_INSTALL_DIR"))
  val testDir = installDir / "test"

  if !os.exists(testDir) then
    Output.info("No E2E tests found (missing test/)")
    true
  else
    val testFiles = os.list(testDir).filter(_.ext == "bats")
    if testFiles.isEmpty then
      Output.info("No BATS test files found")
      true
    else
      // Pre-build the core jar once so BATS tests inherit it via IW_CORE_JAR,
      // avoiding per-test 30s jar rebuilds.
      Output.section("Pre-building core jar for E2E tests")
      val bootstrapExit = ProcessAdapter.runStreaming(
        Seq((installDir / "iw-run").toString, "--bootstrap"),
        timeoutMs = 10 * 60 * 1000
      )
      if bootstrapExit != 0 then
        Output.error("Failed to pre-build core jar; aborting E2E run")
        return false

      val coreJar = installDir / "build" / "iw-core.jar"

      Output.section("Running E2E Tests")

      // Run each BATS file individually to avoid temp directory race conditions
      val sortedFiles = testFiles.sortBy(_.last)
      val results = sortedFiles.map { testFile =>
        ProcessAdapter.runStreaming(
          Seq("bats", testFile.toString),
          timeoutMs = 10 * 60 * 1000,
          env = Map("IW_CORE_JAR" -> coreJar.toString)
        ) == 0
      }
      results.forall(identity)
