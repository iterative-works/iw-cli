// PURPOSE: Unit tests for GitStatusService application logic
// PURPOSE: Tests git status detection with mocked command execution

package iw.core.application

import munit.FunSuite
import iw.core.domain.GitStatus

class GitStatusServiceTest extends FunSuite:

  test("getGitStatus returns status when git commands succeed"):
    val execCommand = (cmd: String, args: Array[String]) =>
      if args.contains("rev-parse") then Right("main")
      else Right("") // Empty status = clean

    val result = GitStatusService.getGitStatus("/path/to/worktree", execCommand)

    assert(result.isRight)
    assertEquals(result.map(_.branchName), Right("main"))
    assertEquals(result.map(_.isClean), Right(true))

  test("getGitStatus handles dirty working tree (modified file)"):
    val execCommand = (cmd: String, args: Array[String]) =>
      if args.contains("rev-parse") then Right("feature")
      else Right(" M file.txt\n") // Modified file

    val result = GitStatusService.getGitStatus("/path/to/worktree", execCommand)

    assert(result.isRight)
    assertEquals(result.map(_.branchName), Right("feature"))
    assertEquals(result.map(_.isClean), Right(false))

  test("getGitStatus handles dirty working tree (untracked file)"):
    val execCommand = (cmd: String, args: Array[String]) =>
      if args.contains("rev-parse") then Right("main")
      else Right("?? new-file.txt\n") // Untracked file

    val result = GitStatusService.getGitStatus("/path/to/worktree", execCommand)

    assert(result.isRight)
    assertEquals(result.map(_.isClean), Right(false))

  test("getGitStatus returns error when git command fails"):
    val execCommand = (cmd: String, args: Array[String]) =>
      Left("Not a git repository")

    val result = GitStatusService.getGitStatus("/path/to/worktree", execCommand)

    assert(result.isLeft)

  test("parseBranchName extracts branch from output"):
    assertEquals(GitStatusService.parseBranchName("main\n"), Some("main"))
    assertEquals(GitStatusService.parseBranchName("feature-branch"), Some("feature-branch"))
    assertEquals(GitStatusService.parseBranchName("HEAD"), Some("HEAD"))

  test("parseBranchName handles empty output"):
    assertEquals(GitStatusService.parseBranchName(""), None)
    assertEquals(GitStatusService.parseBranchName("   \n"), None)

  test("isWorkingTreeClean returns true for empty output"):
    assert(GitStatusService.isWorkingTreeClean(""))
    assert(GitStatusService.isWorkingTreeClean("\n"))
    assert(GitStatusService.isWorkingTreeClean("   \n   "))

  test("isWorkingTreeClean returns false for modified file"):
    assert(!GitStatusService.isWorkingTreeClean(" M file.txt"))
    assert(!GitStatusService.isWorkingTreeClean(" M file.txt\n"))

  test("isWorkingTreeClean returns false for untracked file"):
    assert(!GitStatusService.isWorkingTreeClean("?? new-file.txt"))
    assert(!GitStatusService.isWorkingTreeClean("?? new-file.txt\n"))

  test("isWorkingTreeClean returns false for staged change"):
    assert(!GitStatusService.isWorkingTreeClean("M  file.txt"))
    assert(!GitStatusService.isWorkingTreeClean("A  new-file.txt\n"))

  test("isWorkingTreeClean returns false for deleted file"):
    assert(!GitStatusService.isWorkingTreeClean(" D file.txt"))
    assert(!GitStatusService.isWorkingTreeClean("D  file.txt\n"))

  test("isWorkingTreeClean returns false for multiple changes"):
    assert(!GitStatusService.isWorkingTreeClean(" M file1.txt\n M file2.txt\n?? file3.txt"))

  test("getGitStatus uses git -C flag with worktree path"):
    var capturedArgs: Option[Array[String]] = None
    val execCommand = (cmd: String, args: Array[String]) =>
      capturedArgs = Some(args)
      if args.contains("rev-parse") then Right("main")
      else Right("")

    GitStatusService.getGitStatus("/custom/path", execCommand)

    assert(capturedArgs.isDefined)
    assert(capturedArgs.get.contains("-C"))
    assert(capturedArgs.get.contains("/custom/path"))

  test("getGitStatus handles branch name with slashes"):
    val execCommand = (cmd: String, args: Array[String]) =>
      if args.contains("rev-parse") then Right("feature/IWLE-100-phase-06")
      else Right("")

    val result = GitStatusService.getGitStatus("/path", execCommand)

    assertEquals(result.map(_.branchName), Right("feature/IWLE-100-phase-06"))
