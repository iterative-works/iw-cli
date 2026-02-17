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
  val projectDir = os.Path(System.getProperty(Constants.SystemProps.UserDir))
  val iwDir = projectDir / Constants.Paths.IwDir
  val testDir = iwDir / "core" / "test"
  val coreDir = iwDir / "core"

  if !os.exists(testDir) then
    Output.info("No unit tests found (missing .iw/core/test/)")
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
      val exitCode = ProcessAdapter.runStreaming(command)
      exitCode == 0

def runCommandCompileCheck(): Boolean =
  val projectDir = os.Path(System.getProperty(Constants.SystemProps.UserDir))
  val iwDir = projectDir / Constants.Paths.IwDir
  val commandsDir = iwDir / "commands"
  val coreDir = iwDir / "core"

  if !os.exists(commandsDir) then
    Output.info("No commands directory found (missing .iw/commands/)")
    true
  else
    val commandFiles = os.list(commandsDir).filter(_.ext == "scala")
    if commandFiles.isEmpty then
      Output.info("No command files found")
      true
    else
      Output.section("Checking Command Compilation")
      Output.info(s"Compiling ${commandFiles.length} commands...")

      val results = commandFiles.sorted.map { commandFile =>
        val commandName = commandFile.last
        // Compile each command with the core module (suppress output unless there's an error)
        val result = ProcessAdapter.run(
          Seq("scala-cli", "compile", coreDir.toString, commandFile.toString, "--quiet")
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
  val projectDir = os.Path(System.getProperty(Constants.SystemProps.UserDir))
  val testDir = projectDir / Constants.Paths.IwDir / "test"

  if !os.exists(testDir) then
    Output.info("No E2E tests found (missing .iw/test/)")
    true
  else
    val testFiles = os.list(testDir).filter(_.ext == "bats")
    if testFiles.isEmpty then
      Output.info("No BATS test files found")
      true
    else
      Output.section("Running E2E Tests")

      // Run each BATS file individually to avoid temp directory race conditions
      val sortedFiles = testFiles.sortBy(_.last)
      val results = sortedFiles.map { testFile =>
        ProcessAdapter.runStreaming(Seq("bats", testFile.toString), timeoutMs = 10 * 60 * 1000) == 0
      }
      results.forall(identity)
