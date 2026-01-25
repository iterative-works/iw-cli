// PURPOSE: Unit tests for ReviewState and ReviewArtifact domain models
// PURPOSE: Verify structure and basic properties of review state data

package iw.core.test

import iw.core.model.{ReviewState, ReviewArtifact}

class ReviewStateTest extends munit.FunSuite:

  test("ReviewState requires artifacts list"):
    val artifact = ReviewArtifact("Analysis", "project-management/issues/46/analysis.md")
    val state = ReviewState(
      status = Some("awaiting_review"),
      phase = Some(8),
      message = Some("Ready for review"),
      artifacts = List(artifact)
    )

    assertEquals(state.artifacts.size, 1)
    assertEquals(state.artifacts.head, artifact)

  test("ReviewState accepts optional status, phase, message"):
    val artifact = ReviewArtifact("Doc", "path/to/doc.md")
    val minimalState = ReviewState(
      status = None,
      phase = None,
      message = None,
      artifacts = List(artifact)
    )

    assertEquals(minimalState.status, None)
    assertEquals(minimalState.phase, None)
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
    val state = ReviewState(None, None, None, artifacts)

    assertEquals(state.artifacts.size, 3)
    assertEquals(state.artifacts.map(_.label), List("Analysis", "Context", "Tasks"))

  test("ReviewState can have empty artifacts list"):
    val state = ReviewState(None, None, None, List.empty)

    assertEquals(state.artifacts.size, 0)
