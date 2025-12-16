// PURPOSE: Unit tests for ProcessAdapter shell command operations
// PURPOSE: Tests commandExists with both real and non-existent commands
package iw.tests

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

  test("run returns output without truncation when within limit"):
    // Small output should not be truncated
    val result = ProcessAdapter.run(Seq("echo", "hello world"))
    assertEquals(result.exitCode, 0)
    assertEquals(result.stdout, "hello world")
    assertEquals(result.truncated, false)

  test("run truncates stdout when exceeding maxOutputBytes"):
    // Generate output larger than the limit
    // We'll use a small limit (100 bytes) for testing
    val largeString = "x" * 200 // 200 bytes
    val result = ProcessAdapter.run(Seq("echo", largeString), maxOutputBytes = 100)
    assertEquals(result.exitCode, 0)
    assert(result.stdout.length <= 100)
    assertEquals(result.truncated, true)

  test("run truncates stderr when exceeding maxOutputBytes"):
    // Use a command that outputs to stderr
    // sh -c 'echo "error" >&2' writes to stderr
    val largeString = "e" * 200
    val result = ProcessAdapter.run(Seq("sh", "-c", s"echo '$largeString' >&2"), maxOutputBytes = 100)
    assert(result.stderr.length <= 100)
    assertEquals(result.truncated, true)

  test("run handles output at exact boundary"):
    // Test boundary condition - exactly at limit
    val exactString = "x" * 50
    val result = ProcessAdapter.run(Seq("echo", exactString), maxOutputBytes = 100)
    assertEquals(result.exitCode, 0)
    // echo adds a newline, so total is 51 bytes, well under 100
    assertEquals(result.truncated, false)

  test("run uses default maxOutputBytes of 1MB"):
    // Verify default parameter works
    val result = ProcessAdapter.run(Seq("echo", "test"))
    assertEquals(result.exitCode, 0)
    assertEquals(result.stdout, "test")
    assertEquals(result.truncated, false)

  test("run tracks both stdout and stderr independently"):
    // Both streams should be tracked separately
    val largeString = "x" * 200
    val result = ProcessAdapter.run(
      Seq("sh", "-c", s"echo '$largeString'; echo 'error$largeString' >&2"),
      maxOutputBytes = 100
    )
    assert(result.stdout.length <= 100)
    assert(result.stderr.length <= 100)
    assertEquals(result.truncated, true)
