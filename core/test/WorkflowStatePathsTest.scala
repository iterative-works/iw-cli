// PURPOSE: Unit tests for WorkflowStatePaths model
// PURPOSE: Verifies correct partitioning of dirty paths into state-owned and user-owned

package iw.core.model

import munit.FunSuite

class WorkflowStatePathsTest extends FunSuite:

  test("partition: only state files → all state, no user"):
    val paths = List(
      "project-management/issues/IW-344/review-state.json",
      "project-management/issues/IW-344/phase-01-context.md"
    )
    val (state, user) = WorkflowStatePaths.partition(paths, "IW-344")
    assertEquals(state, paths)
    assertEquals(user, Nil)

  test("partition: only user files → all user, no state"):
    val paths = List(
      "src/Main.scala",
      "core/adapters/Git.scala"
    )
    val (state, user) = WorkflowStatePaths.partition(paths, "IW-344")
    assertEquals(state, Nil)
    assertEquals(user, paths)

  test("partition: mixed → correctly split"):
    val paths = List(
      "project-management/issues/IW-344/review-state.json",
      "src/Main.scala",
      "project-management/issues/IW-344/phase-02-tasks.md",
      "iw-run"
    )
    val (state, user) = WorkflowStatePaths.partition(paths, "IW-344")
    assertEquals(
      state,
      List(
        "project-management/issues/IW-344/review-state.json",
        "project-management/issues/IW-344/phase-02-tasks.md"
      )
    )
    assertEquals(user, List("src/Main.scala", "iw-run"))

  test("partition: other issue's state files count as user"):
    // If the tree has dirt under a different issue's state dir, that's not
    // ours to auto-commit — treat as user-owned so the user decides.
    val paths = List(
      "project-management/issues/IW-999/review-state.json"
    )
    val (state, user) = WorkflowStatePaths.partition(paths, "IW-344")
    assertEquals(state, Nil)
    assertEquals(user, paths)

  test("partition: empty input → empty partitions"):
    val (state, user) = WorkflowStatePaths.partition(Nil, "IW-344")
    assertEquals(state, Nil)
    assertEquals(user, Nil)

  test("stateDir: produces trailing-slash prefix"):
    assertEquals(
      WorkflowStatePaths.stateDir("IW-344"),
      "project-management/issues/IW-344/"
    )
