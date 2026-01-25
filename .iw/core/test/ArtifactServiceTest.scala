// PURPOSE: Unit tests for ArtifactService artifact loading and rendering
// PURPOSE: Verify path validation integration, file reading, and markdown rendering

package iw.core.test

import iw.core.dashboard.ArtifactService
import iw.core.model.{ServerState, WorktreeRegistration}
import java.nio.file.{Path, Paths}
import java.time.Instant

class ArtifactServiceTest extends munit.FunSuite:

  val testWorktree = WorktreeRegistration(
    issueId = "TEST-123",
    path = "/tmp/test-worktree",
    trackerType = "github",
    team = "test-team",
    registeredAt = Instant.now(),
    lastSeenAt = Instant.now()
  )

  val testState = ServerState(
    worktrees = Map("TEST-123" -> testWorktree)
  )

  // Mock symlink resolver that just returns the path unchanged (for testing)
  val mockSymlinkResolver = (path: Path) => Right(path)

  test("loadArtifact succeeds with valid issueId and path"):
    val readFile = (path: Path) => Right("# Test Markdown")

    val result = ArtifactService.loadArtifact(
      issueId = "TEST-123",
      artifactPath = "analysis.md",
      state = testState,
      readFile = readFile,
      resolveSymlinks = mockSymlinkResolver
    )

    assert(result.isRight)
    val (label, html, worktreePath) = result.toOption.get
    assertEquals(label, "analysis.md")
    assert(html.contains("<h1>"))
    assert(html.contains("Test Markdown"))
    assertEquals(worktreePath, "/tmp/test-worktree")

  test("loadArtifact returns Left when worktree not found"):
    val readFile = (path: Path) => Right("content")

    val result = ArtifactService.loadArtifact(
      issueId = "NONEXISTENT",
      artifactPath = "analysis.md",
      state = testState,
      readFile = readFile,
      resolveSymlinks = mockSymlinkResolver
    )

    assert(result.isLeft)
    assertEquals(result.left.toOption.get, "Worktree not found")

  test("loadArtifact returns Left when PathValidator rejects path (absolute path)"):
    val readFile = (path: Path) => Right("content")

    val result = ArtifactService.loadArtifact(
      issueId = "TEST-123",
      artifactPath = "/etc/passwd",
      state = testState,
      readFile = readFile,
      resolveSymlinks = mockSymlinkResolver
    )

    assert(result.isLeft)
    assertEquals(result.left.toOption.get, "Artifact path must be relative")

  test("loadArtifact returns Left when PathValidator rejects path (directory traversal)"):
    val readFile = (path: Path) => Right("content")

    val result = ArtifactService.loadArtifact(
      issueId = "TEST-123",
      artifactPath = "../../etc/passwd",
      state = testState,
      readFile = readFile,
      resolveSymlinks = mockSymlinkResolver
    )

    assert(result.isLeft)
    assertEquals(result.left.toOption.get, "Artifact not found")

  test("loadArtifact returns Left when file read fails"):
    val readFile = (path: Path) => Left("File not found")

    val result = ArtifactService.loadArtifact(
      issueId = "TEST-123",
      artifactPath = "analysis.md",
      state = testState,
      readFile = readFile,
      resolveSymlinks = mockSymlinkResolver
    )

    assert(result.isLeft)
    // The readFile error is propagated through
    assertEquals(result.left.toOption.get, "File not found")

  test("loadArtifact integrates with MarkdownRenderer"):
    val markdown = """# Heading
                     |
                     |Some **bold** text.
                     |
                     |- Item 1
                     |- Item 2""".stripMargin
    val readFile = (path: Path) => Right(markdown)

    val result = ArtifactService.loadArtifact(
      issueId = "TEST-123",
      artifactPath = "doc.md",
      state = testState,
      readFile = readFile,
      resolveSymlinks = mockSymlinkResolver
    )

    assert(result.isRight)
    val (label, html, _) = result.toOption.get

    // Verify markdown was rendered
    assert(html.contains("<h1>"))
    assert(html.contains("Heading"))
    assert(html.contains("<strong>"))
    assert(html.contains("bold"))
    assert(html.contains("<ul>"))
    assert(html.contains("<li>"))

  test("extractLabel returns filename from path"):
    val label1 = ArtifactService.extractLabel(Paths.get("analysis.md"))
    assertEquals(label1, "analysis.md")

    val label2 = ArtifactService.extractLabel(Paths.get("project-management/issues/46/analysis.md"))
    assertEquals(label2, "analysis.md")

    val label3 = ArtifactService.extractLabel(Paths.get("/absolute/path/to/file.txt"))
    assertEquals(label3, "file.txt")

  test("extractLabel handles single filename"):
    val label = ArtifactService.extractLabel(Paths.get("README.md"))
    assertEquals(label, "README.md")

  test("loadArtifact returns correct worktree path"):
    val readFile = (path: Path) => Right("content")

    val result = ArtifactService.loadArtifact(
      issueId = "TEST-123",
      artifactPath = "analysis.md",
      state = testState,
      readFile = readFile,
      resolveSymlinks = mockSymlinkResolver
    )

    assert(result.isRight)
    val (_, _, worktreePath) = result.toOption.get
    assertEquals(worktreePath, "/tmp/test-worktree")

  test("loadArtifact handles nested artifact paths"):
    val readFile = (path: Path) => Right("# Nested")

    val result = ArtifactService.loadArtifact(
      issueId = "TEST-123",
      artifactPath = "project-management/issues/TEST-123/analysis.md",
      state = testState,
      readFile = readFile,
      resolveSymlinks = mockSymlinkResolver
    )

    assert(result.isRight)
    val (label, html, _) = result.toOption.get
    assertEquals(label, "analysis.md")
    assert(html.contains("Nested"))

  test("loadArtifact preserves markdown rendering errors"):
    // Even if markdown is malformed, MarkdownRenderer should handle it gracefully
    val readFile = (path: Path) => Right("")

    val result = ArtifactService.loadArtifact(
      issueId = "TEST-123",
      artifactPath = "empty.md",
      state = testState,
      readFile = readFile,
      resolveSymlinks = mockSymlinkResolver
    )

    assert(result.isRight)
    val (label, html, _) = result.toOption.get
    assertEquals(label, "empty.md")
    assertEquals(html, "") // Empty markdown renders to empty HTML
