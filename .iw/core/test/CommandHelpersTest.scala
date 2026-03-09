// PURPOSE: Unit tests for CommandHelpers CLI exit utilities
// PURPOSE: Verifies that success paths unwrap values without side effects

package iw.tests

import munit.FunSuite
import iw.core.output.CommandHelpers

class CommandHelpersTest extends FunSuite:

  test("exitOnError returns the value for Right"):
    val result = CommandHelpers.exitOnError(Right(42))
    assertEquals(result, 42)

  test("exitOnError returns a string value for Right"):
    val result = CommandHelpers.exitOnError(Right("hello"))
    assertEquals(result, "hello")

  test("exitOnNone returns the value for Some"):
    val result = CommandHelpers.exitOnNone(Some("hello"), "unused error")
    assertEquals(result, "hello")

  test("exitOnNone returns an integer for Some"):
    val result = CommandHelpers.exitOnNone(Some(99), "unused error")
    assertEquals(result, 99)
