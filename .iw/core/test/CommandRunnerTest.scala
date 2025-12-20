// PURPOSE: Unit tests for CommandRunner infrastructure
// PURPOSE: Tests shell command execution and tool availability detection

package iw.core.infrastructure

import munit.FunSuite
import java.io.File

class CommandRunnerTest extends FunSuite:

  test("execute returns stdout when command succeeds"):
    val result = CommandRunner.execute("echo", Array("test"))
    assertEquals(result, Right("test"))

  test("execute trims whitespace from output"):
    val result = CommandRunner.execute("echo", Array("  test  "))
    assertEquals(result, Right("test"))

  test("execute returns Left when command fails (non-zero exit)"):
    val result = CommandRunner.execute("false", Array())
    assert(result.isLeft, "false command should fail")

  test("execute works with multiple arguments"):
    val result = CommandRunner.execute("echo", Array("hello", "world"))
    assert(result.isRight)
    assert(result.toOption.get.contains("hello"))
    assert(result.toOption.get.contains("world"))

  test("execute handles command not found"):
    val result = CommandRunner.execute("nonexistent-command-xyz-123", Array())
    assert(result.isLeft, "Non-existent command should return Left")

  test("execute works with workingDir parameter"):
    val tempDir = File("/tmp")
    val result = CommandRunner.execute("pwd", Array(), Some("/tmp"))
    assert(result.isRight)
    // Output should contain /tmp in some form

  test("isCommandAvailable returns true for existing command (echo)"):
    val available = CommandRunner.isCommandAvailable("echo")
    assert(available, "echo should be available")

  test("isCommandAvailable returns true for existing command (git)"):
    val available = CommandRunner.isCommandAvailable("git")
    assert(available, "git should be available")

  test("isCommandAvailable returns false for non-existent command"):
    val available = CommandRunner.isCommandAvailable("nonexistent-command-xyz-123")
    assert(!available, "Non-existent command should not be available")

  test("execute with git command"):
    val result = CommandRunner.execute("git", Array("--version"))
    assert(result.isRight)
    assert(result.toOption.get.contains("git version"))
