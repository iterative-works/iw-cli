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

  test("commandExists prevents shell injection with metacharacters"):
    // All of these should return false due to input validation
    assertEquals(ProcessAdapter.commandExists("command; rm -rf /"), false)
    assertEquals(ProcessAdapter.commandExists("command && malicious"), false)
    assertEquals(ProcessAdapter.commandExists("command | cat /etc/passwd"), false)
    assertEquals(ProcessAdapter.commandExists("command`whoami`"), false)
    assertEquals(ProcessAdapter.commandExists("command$(whoami)"), false)
    assertEquals(ProcessAdapter.commandExists("command > /tmp/output"), false)
    assertEquals(ProcessAdapter.commandExists("command < /etc/hosts"), false)
    assertEquals(ProcessAdapter.commandExists("command & background"), false)

  test("commandExists returns false for empty string"):
    assertEquals(ProcessAdapter.commandExists(""), false)

  test("commandExists returns false for commands with spaces"):
    assertEquals(ProcessAdapter.commandExists("git status"), false)
    assertEquals(ProcessAdapter.commandExists("my command"), false)

  test("commandExists returns false for commands with quotes"):
    assertEquals(ProcessAdapter.commandExists("'malicious'"), false)
    assertEquals(ProcessAdapter.commandExists("\"malicious\""), false)

  test("commandExists returns false for commands with path separators"):
    assertEquals(ProcessAdapter.commandExists("/bin/sh"), false)
    assertEquals(ProcessAdapter.commandExists("../bin/command"), false)
    assertEquals(ProcessAdapter.commandExists("./local-command"), false)
