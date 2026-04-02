// PURPOSE: Unit tests for StagingCheck model
// PURPOSE: Tests pure validation of worktree cleanliness for phase-commit

package iw.core.model

import munit.FunSuite

class StagingCheckTest extends FunSuite:

  test("clean worktree with staged changes is ready"):
    val check = StagingCheck(
      stagedFiles = List("src/Main.scala"),
      unstagedFiles = Nil,
      untrackedFiles = Nil
    )
    assertEquals(check.readyToCommit, Right(()))

  test("no staged changes returns error"):
    val check = StagingCheck(
      stagedFiles = Nil,
      unstagedFiles = Nil,
      untrackedFiles = Nil
    )
    check.readyToCommit match
      case Left(err) =>
        assert(err.contains("No staged changes"), s"Unexpected error: $err")
      case Right(_) => fail("Expected Left")

  test("unstaged modifications returns error listing files"):
    val check = StagingCheck(
      stagedFiles = List("src/Main.scala"),
      unstagedFiles = List("src/Other.scala", "build.sbt"),
      untrackedFiles = Nil
    )
    check.readyToCommit match
      case Left(err) =>
        assert(err.contains("src/Other.scala"), s"Expected file in error: $err")
        assert(err.contains("build.sbt"), s"Expected file in error: $err")
      case Right(_) => fail("Expected Left")

  test("untracked files returns error listing files"):
    val check = StagingCheck(
      stagedFiles = List("src/Main.scala"),
      unstagedFiles = Nil,
      untrackedFiles = List("temp.txt", "notes.md")
    )
    check.readyToCommit match
      case Left(err) =>
        assert(err.contains("temp.txt"), s"Expected file in error: $err")
        assert(err.contains("notes.md"), s"Expected file in error: $err")
      case Right(_) => fail("Expected Left")

  test("both unstaged and untracked returns error mentioning both"):
    val check = StagingCheck(
      stagedFiles = List("src/Main.scala"),
      unstagedFiles = List("src/Other.scala"),
      untrackedFiles = List("temp.txt")
    )
    check.readyToCommit match
      case Left(err) =>
        assert(err.contains("src/Other.scala"), s"Expected unstaged file: $err")
        assert(err.contains("temp.txt"), s"Expected untracked file: $err")
      case Right(_) => fail("Expected Left")

  test("no staged changes with unstaged files reports all issues"):
    val check = StagingCheck(
      stagedFiles = Nil,
      unstagedFiles = List("src/Other.scala"),
      untrackedFiles = Nil
    )
    check.readyToCommit match
      case Left(err) =>
        assert(
          err.contains("No staged changes"),
          s"Expected no-staged error: $err"
        )
        assert(err.contains("src/Other.scala"), s"Expected unstaged file: $err")
      case Right(_) => fail("Expected Left")

  // fromPorcelain parsing tests

  test("fromPorcelain parses staged added file"):
    val check = StagingCheck.fromPorcelain("A  src/New.scala")
    assertEquals(check.stagedFiles, List("src/New.scala"))
    assertEquals(check.unstagedFiles, Nil)
    assertEquals(check.untrackedFiles, Nil)

  test("fromPorcelain parses staged modified file"):
    val check = StagingCheck.fromPorcelain("M  src/Main.scala")
    assertEquals(check.stagedFiles, List("src/Main.scala"))
    assertEquals(check.unstagedFiles, Nil)

  test("fromPorcelain parses unstaged modified file"):
    val check = StagingCheck.fromPorcelain(" M src/Main.scala")
    assertEquals(check.stagedFiles, Nil)
    assertEquals(check.unstagedFiles, List("src/Main.scala"))

  test("fromPorcelain parses untracked file"):
    val check = StagingCheck.fromPorcelain("?? temp.txt")
    assertEquals(check.stagedFiles, Nil)
    assertEquals(check.unstagedFiles, Nil)
    assertEquals(check.untrackedFiles, List("temp.txt"))

  test("fromPorcelain skips ignored files"):
    val check = StagingCheck.fromPorcelain("!! .idea/workspace.xml")
    assertEquals(check.stagedFiles, Nil)
    assertEquals(check.unstagedFiles, Nil)
    assertEquals(check.untrackedFiles, Nil)

  test("fromPorcelain handles mixed status (staged + unstaged)"):
    val check = StagingCheck.fromPorcelain("MM src/Main.scala")
    assertEquals(check.stagedFiles, List("src/Main.scala"))
    assertEquals(check.unstagedFiles, List("src/Main.scala"))

  test("fromPorcelain parses multiple lines"):
    val output = "A  src/New.scala\n M src/Old.scala\n?? notes.txt"
    val check = StagingCheck.fromPorcelain(output)
    assertEquals(check.stagedFiles, List("src/New.scala"))
    assertEquals(check.unstagedFiles, List("src/Old.scala"))
    assertEquals(check.untrackedFiles, List("notes.txt"))

  test("fromPorcelain handles empty output"):
    val check = StagingCheck.fromPorcelain("")
    assertEquals(check.stagedFiles, Nil)
    assertEquals(check.unstagedFiles, Nil)
    assertEquals(check.untrackedFiles, Nil)

  test("fromPorcelain parses staged deleted file"):
    val check = StagingCheck.fromPorcelain("D  src/Old.scala")
    assertEquals(check.stagedFiles, List("src/Old.scala"))
    assertEquals(check.unstagedFiles, Nil)

  test("fromPorcelain parses renamed file"):
    val check = StagingCheck.fromPorcelain("R  old.scala -> new.scala")
    assertEquals(check.stagedFiles, List("old.scala -> new.scala"))
    assertEquals(check.unstagedFiles, Nil)
