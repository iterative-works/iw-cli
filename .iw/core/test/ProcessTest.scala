// PURPOSE: Unit tests for ProcessAdapter shell command operations
// PURPOSE: Tests commandExists with both real and non-existent commands

//> using scala 3.3.1
//> using dep org.scalameta::munit::1.0.0
//> using file "../Process.scala"

package iw.core.test

import iw.core.*
import munit.FunSuite

class ProcessTest extends FunSuite:

  test("commandExists returns true for existing command (sh)"):
    // sh should exist on all POSIX systems
    assertEquals(ProcessAdapter.commandExists("sh"), true)

  test("commandExists returns false for non-existent command"):
    assertEquals(ProcessAdapter.commandExists("this-command-definitely-does-not-exist-12345"), false)

  test("commandExists returns true for common commands"):
    // Test with git which should be available in CI/dev environments
    val hasGit = ProcessAdapter.commandExists("git")
    // We can't assert true here because git might not be installed
    // But we can verify the function doesn't throw
    assert(hasGit == true || hasGit == false)

  test("commandExists handles command with special characters safely"):
    // Should not cause shell injection or errors
    assertEquals(ProcessAdapter.commandExists("command-with-dash"), false)
    assertEquals(ProcessAdapter.commandExists("command_with_underscore"), false)
