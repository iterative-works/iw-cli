// PURPOSE: Unit tests for ReviewState and ReviewArtifact domain models
// PURPOSE: Verify structure and basic properties of review state data

package iw.core.test

import iw.core.model.{ReviewState, ReviewArtifact, Display}

class ReviewStateTest extends munit.FunSuite:

  test("ReviewState requires artifacts list"):
    val artifact = ReviewArtifact("Analysis", "project-management/issues/46/analysis.md")
    val state = ReviewState(
      display = Some(Display("Awaiting Review", Some("Phase 8"), "warning")),
      badges = None,
      taskLists = None,
      needsAttention = None,
      message = Some("Ready for review"),
      artifacts = List(artifact)
    )

    assertEquals(state.artifacts.size, 1)
    assertEquals(state.artifacts.head, artifact)

  test("ReviewState accepts all optional fields as None"):
    val artifact = ReviewArtifact("Doc", "path/to/doc.md")
    val minimalState = ReviewState(
      display = None,
      badges = None,
      taskLists = None,
      needsAttention = None,
      message = None,
      artifacts = List(artifact)
    )

    assertEquals(minimalState.display, None)
    assertEquals(minimalState.badges, None)
    assertEquals(minimalState.taskLists, None)
    assertEquals(minimalState.needsAttention, None)
    assertEquals(minimalState.message, None)
    assertEquals(minimalState.artifacts.size, 1)

  test("ReviewArtifact has label and path"):
    val artifact = ReviewArtifact("Analysis", "project-management/issues/46/analysis.md")

    assertEquals(artifact.label, "Analysis")
    assertEquals(artifact.path, "project-management/issues/46/analysis.md")

  test("ReviewState can have multiple artifacts"):
    val artifacts = List(
      ReviewArtifact("Analysis", "path/to/analysis.md"),
      ReviewArtifact("Context", "path/to/context.md"),
      ReviewArtifact("Tasks", "path/to/tasks.md")
    )
    val state = ReviewState(None, None, None, None, None, artifacts)

    assertEquals(state.artifacts.size, 3)
    assertEquals(state.artifacts.map(_.label), List("Analysis", "Context", "Tasks"))

  test("ReviewState can have empty artifacts list"):
    val state = ReviewState(None, None, None, None, None, List.empty)

    assertEquals(state.artifacts.size, 0)
