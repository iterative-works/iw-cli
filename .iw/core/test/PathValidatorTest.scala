// PURPOSE: Unit tests for artifact path validation
// PURPOSE: Tests PathValidator security validation for directory traversal prevention
package iw.tests

import iw.core.*
import munit.FunSuite
import java.nio.file.{Path, Paths}
import java.io.File

class PathValidatorTest extends FunSuite:

  // Helper to create test worktree path
  val worktreePath: Path = Paths.get("/tmp/test-worktree").toAbsolutePath

  // Mock symlink resolver that just returns the path (for non-I/O tests)
  val noOpResolver: Path => Either[String, Path] = path => Right(path)

  test("validateArtifactPath accepts valid relative path within worktree"):
    val result = PathValidator.validateArtifactPath(
      worktreePath,
      "project-management/issues/46/analysis.md",
      noOpResolver
    )

    assert(result.isRight, "Expected Right but got Left")
    val resolvedPath = result.toOption.get
    assert(resolvedPath.toString.contains("project-management"), "Path should contain project-management")

  test("validateArtifactPath rejects empty path"):
    val result = PathValidator.validateArtifactPath(worktreePath, "", noOpResolver)

    assert(result.isLeft)
    assertEquals(result.swap.toOption.get, "Invalid artifact path")

  test("validateArtifactPath rejects whitespace-only path"):
    val result = PathValidator.validateArtifactPath(worktreePath, "   ", noOpResolver)

    assert(result.isLeft)
    assertEquals(result.swap.toOption.get, "Invalid artifact path")

  test("validateArtifactPath rejects absolute Unix path"):
    val result = PathValidator.validateArtifactPath(worktreePath, "/etc/passwd", noOpResolver)

    assert(result.isLeft)
    assertEquals(result.swap.toOption.get, "Artifact path must be relative")

  test("validateArtifactPath rejects absolute Windows path"):
    val result = PathValidator.validateArtifactPath(worktreePath, "C:\\Windows\\System32", noOpResolver)

    assert(result.isLeft)
    assertEquals(result.swap.toOption.get, "Artifact path must be relative")

  test("validateArtifactPath rejects simple traversal"):
    val result = PathValidator.validateArtifactPath(worktreePath, "../../../etc/passwd", noOpResolver)

    assert(result.isLeft)
    assertEquals(result.swap.toOption.get, "Artifact not found")

  test("validateArtifactPath rejects embedded traversal"):
    val result = PathValidator.validateArtifactPath(worktreePath, "project-management/../../../etc/passwd", noOpResolver)

    assert(result.isLeft)
    assertEquals(result.swap.toOption.get, "Artifact not found")

  test("validateArtifactPath accepts path with .. that stays within boundary"):
    val result = PathValidator.validateArtifactPath(worktreePath, "project-management/issues/../tasks.md", noOpResolver)

    assert(result.isRight, s"Expected Right but got Left: ${result.swap.toOption}")
    val resolvedPath = result.toOption.get
    // Path should be normalized to project-management/tasks.md
    assert(resolvedPath.toString.contains("project-management"), "Path should contain project-management")

  test("validateArtifactPath accepts filename containing .. like file..name.md"):
    val result = PathValidator.validateArtifactPath(worktreePath, "docs/file..name.md", noOpResolver)

    assert(result.isRight, "Should accept filenames with .. in them")
    val resolvedPath = result.toOption.get
    assert(resolvedPath.toString.contains("file..name.md"), "Filename should be preserved")

  test("isWithinBoundary returns true when path is under base"):
    val basePath = Paths.get("/home/user/project")
    val targetPath = Paths.get("/home/user/project/src/main.scala")

    assert(PathValidator.isWithinBoundary(basePath, targetPath))

  test("isWithinBoundary returns false when path escapes base"):
    val basePath = Paths.get("/home/user/project")
    val targetPath = Paths.get("/home/user/other/file.txt")

    assert(!PathValidator.isWithinBoundary(basePath, targetPath))

  test("isWithinBoundary handles equal paths"):
    val basePath = Paths.get("/home/user/project")
    val targetPath = Paths.get("/home/user/project")

    assert(PathValidator.isWithinBoundary(basePath, targetPath))

  test("validateArtifactPath rejects symlink pointing outside worktree"):
    // Create temp directory structure for testing
    val testDir = Paths.get("/tmp/pathvalidator-test-" + System.currentTimeMillis())
    val worktree = testDir.resolve("worktree")
    val outside = testDir.resolve("outside")

    try
      java.nio.file.Files.createDirectories(worktree)
      java.nio.file.Files.createDirectories(outside)
      java.nio.file.Files.writeString(outside.resolve("secret.txt"), "secret data")

      // Create symlink inside worktree pointing outside
      val symlinkPath = worktree.resolve("escape-link")
      java.nio.file.Files.createSymbolicLink(symlinkPath, outside.resolve("secret.txt"))

      val result = PathValidator.validateArtifactPath(worktree, "escape-link")

      assert(result.isLeft, "Should reject symlink pointing outside worktree")
      assertEquals(result.swap.toOption.get, "Artifact not found")
    finally
      // Clean up
      scala.util.Try(deleteRecursively(testDir))

  test("validateArtifactPath accepts symlink pointing within worktree"):
    // Create temp directory structure for testing
    val testDir = Paths.get("/tmp/pathvalidator-test-" + System.currentTimeMillis())
    val worktree = testDir.resolve("worktree")

    try
      java.nio.file.Files.createDirectories(worktree)
      java.nio.file.Files.writeString(worktree.resolve("target.txt"), "valid data")

      // Create symlink inside worktree pointing to another file in worktree
      val symlinkPath = worktree.resolve("valid-link")
      java.nio.file.Files.createSymbolicLink(symlinkPath, worktree.resolve("target.txt"))

      val result = PathValidator.validateArtifactPath(worktree, "valid-link")

      assert(result.isRight, s"Should accept symlink pointing within worktree, got: ${result.swap.toOption}")
    finally
      // Clean up
      scala.util.Try(deleteRecursively(testDir))

  test("validateArtifactPath handles broken symlinks gracefully"):
    // Create temp directory structure for testing
    val testDir = Paths.get("/tmp/pathvalidator-test-" + System.currentTimeMillis())
    val worktree = testDir.resolve("worktree")

    try
      java.nio.file.Files.createDirectories(worktree)

      // Create broken symlink (pointing to non-existent file)
      val symlinkPath = worktree.resolve("broken-link")
      java.nio.file.Files.createSymbolicLink(symlinkPath, worktree.resolve("nonexistent.txt"))

      val result = PathValidator.validateArtifactPath(worktree, "broken-link")

      assert(result.isLeft, "Should reject broken symlink")
      assertEquals(result.swap.toOption.get, "Artifact not found")
    finally
      // Clean up
      scala.util.Try(deleteRecursively(testDir))

  test("validateArtifactPath end-to-end with real worktree path"):
    // Create temp directory structure for testing
    val testDir = Paths.get("/tmp/pathvalidator-e2e-test-" + System.currentTimeMillis())
    val worktree = testDir.resolve("worktree")

    try
      java.nio.file.Files.createDirectories(worktree.resolve("project-management/issues/46"))
      java.nio.file.Files.writeString(
        worktree.resolve("project-management/issues/46/analysis.md"),
        "# Analysis"
      )

      // Test with default resolver (real I/O)
      val result = PathValidator.validateArtifactPath(
        worktree,
        "project-management/issues/46/analysis.md"
      )

      assert(result.isRight, s"Should accept valid path with real I/O, got: ${result.swap.toOption}")
      val resolvedPath = result.toOption.get
      assert(java.nio.file.Files.exists(resolvedPath), "Resolved path should exist")
    finally
      scala.util.Try(deleteRecursively(testDir))

  test("validateArtifactPath handles Unicode path components"):
    val result = PathValidator.validateArtifactPath(
      worktreePath,
      "docs/příklad/úkol.md",
      noOpResolver
    )

    assert(result.isRight, "Should accept Unicode path components")
    val resolvedPath = result.toOption.get
    assert(resolvedPath.toString.contains("příklad"), "Path should preserve Unicode characters")

  test("validateArtifactPath handles special characters in filenames"):
    val result = PathValidator.validateArtifactPath(
      worktreePath,
      "docs/file-name_with+special$chars.md",
      noOpResolver
    )

    assert(result.isRight, "Should accept special characters in filenames")
    val resolvedPath = result.toOption.get
    assert(resolvedPath.toString.contains("file-name_with+special$chars.md"), "Filename should be preserved")

  // Helper to recursively delete directories
  private def deleteRecursively(path: Path): Unit =
    if java.nio.file.Files.exists(path) then
      if java.nio.file.Files.isDirectory(path) then
        val stream = java.nio.file.Files.list(path)
        try
          stream.forEach(deleteRecursively)
        finally
          stream.close()
      java.nio.file.Files.delete(path)
