// PURPOSE: Tests for Output utility formatting functions
// PURPOSE: Verifies correct formatting of info, error, success, section, and keyValue output

//> using scala 3.3.1
//> using dep org.scalameta::munit::1.0.0
//> using file "../Output.scala"

import iw.core.Output
import java.io.ByteArrayOutputStream
import java.io.PrintStream

class OutputTest extends munit.FunSuite:

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

  test("info prints message to stdout"):
    val output = captureStdout:
      Output.info("test message")
    assertEquals(output.trim, "test message")

  test("error prints message to stderr with Error prefix"):
    val output = captureStderr:
      Output.error("something failed")
    assertEquals(output.trim, "Error: something failed")

  test("success prints message with checkmark"):
    val output = captureStdout:
      Output.success("operation completed")
    assertEquals(output.trim, "✓ operation completed")

  test("section prints title with formatting"):
    val output = captureStdout:
      Output.section("Test Section")
    // section() prints a leading newline, so we need to handle that
    assert(output.contains("=== Test Section ==="))

  test("keyValue prints formatted key-value pair"):
    val output = captureStdout:
      Output.keyValue("Version", "1.0.0")
    val lines = output.trim.split('\n')
    assertEquals(lines.length, 1)
    assert(lines(0).startsWith("Version"))
    assert(lines(0).contains("1.0.0"))

  test("warning prints message to stdout with Warning prefix"):
    val output = captureStdout:
      Output.warning("potential issue")
    assertEquals(output.trim, "Warning: potential issue")
