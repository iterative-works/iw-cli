// PURPOSE: Tests for Log trait and logging functionality
// PURPOSE: Verifies log level filtering and environment variable configuration

//> using scala 3.3.1
//> using dep org.scalameta::munit::1.2.1
//> using file "../Log.scala"

import iw.core.{Log, LogLevel}
import java.io.ByteArrayOutputStream
import java.io.PrintStream

class LogTest extends munit.FunSuite:

  def captureStdout(f: => Unit): String =
    val baos = ByteArrayOutputStream()
    val ps = PrintStream(baos)
    val oldOut = System.out
    try
      System.setOut(ps)
      f
      ps.flush()
      baos.toString()
    finally
      System.setOut(oldOut)
      ps.close()

  def captureStderr(f: => Unit): String =
    val baos = ByteArrayOutputStream()
    val ps = PrintStream(baos)
    val oldErr = System.err
    try
      System.setErr(ps)
      f
      ps.flush()
      baos.toString()
    finally
      System.setErr(oldErr)
      ps.close()

  test("debug messages are not printed when debug is disabled"):
    val log = Log(debugEnabled = false)
    val output = captureStdout:
      log.debug("debug message")
    assertEquals(output, "")

  test("debug messages are printed when debug is enabled"):
    val log = Log(debugEnabled = true)
    val output = captureStdout:
      log.debug("debug message")
    assert(output.contains("[DEBUG]"))
    assert(output.contains("debug message"))

  test("info messages are always printed to stdout"):
    val log = Log(debugEnabled = false)
    val output = captureStdout:
      log.info("info message")
    assert(output.contains("[INFO]"))
    assert(output.contains("info message"))

  test("warn messages are printed to stdout"):
    val log = Log(debugEnabled = false)
    val output = captureStdout:
      log.warn("warning message")
    assert(output.contains("[WARN]"))
    assert(output.contains("warning message"))

  test("error messages are printed to stderr"):
    val log = Log(debugEnabled = false)
    val output = captureStderr:
      log.error("error message")
    assert(output.contains("[ERROR]"))
    assert(output.contains("error message"))

  test("fromEnv creates Log with debug disabled when IW_DEBUG is not set"):
    val log = Log.fromEnv(Map.empty)
    val output = captureStdout:
      log.debug("debug message")
    assertEquals(output, "")

  test("fromEnv creates Log with debug enabled when IW_DEBUG=1"):
    val log = Log.fromEnv(Map("IW_DEBUG" -> "1"))
    val output = captureStdout:
      log.debug("debug message")
    assert(output.contains("[DEBUG]"))

  test("fromEnv creates Log with debug enabled when IW_DEBUG=true"):
    val log = Log.fromEnv(Map("IW_DEBUG" -> "true"))
    val output = captureStdout:
      log.debug("debug message")
    assert(output.contains("[DEBUG]"))

  test("fromEnv creates Log with debug disabled when IW_DEBUG=0"):
    val log = Log.fromEnv(Map("IW_DEBUG" -> "0"))
    val output = captureStdout:
      log.debug("debug message")
    assertEquals(output, "")

  test("fromEnv creates Log with debug disabled when IW_DEBUG=false"):
    val log = Log.fromEnv(Map("IW_DEBUG" -> "false"))
    val output = captureStdout:
      log.debug("debug message")
    assertEquals(output, "")

  test("fromEnv creates Log with debug disabled for any other value"):
    val log = Log.fromEnv(Map("IW_DEBUG" -> "maybe"))
    val output = captureStdout:
      log.debug("debug message")
    assertEquals(output, "")

  test("log messages contain proper formatting"):
    val log = Log(debugEnabled = true)
    val debugOutput = captureStdout:
      log.debug("test")
    val infoOutput = captureStdout:
      log.info("test")
    val warnOutput = captureStdout:
      log.warn("test")
    val errorOutput = captureStderr:
      log.error("test")

    assertEquals(debugOutput.trim, "[DEBUG] test")
    assertEquals(infoOutput.trim, "[INFO] test")
    assertEquals(warnOutput.trim, "[WARN] test")
    assertEquals(errorOutput.trim, "[ERROR] test")

  test("multiple log calls work correctly"):
    val log = Log(debugEnabled = true)
    val output = captureStdout:
      log.info("first")
      log.warn("second")
      log.debug("third")

    val lines = output.trim.split('\n')
    assertEquals(lines.length, 3)
    assert(lines(0).contains("[INFO] first"))
    assert(lines(1).contains("[WARN] second"))
    assert(lines(2).contains("[DEBUG] third"))
