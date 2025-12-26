// PURPOSE: Domain model for review state and artifacts
// PURPOSE: Represents review state data from review-state.json files

package iw.core.domain

/** Artifact available for review.
  *
  * @param label Human-readable label for the artifact (e.g., "Analysis", "Phase Context")
  * @param path Relative path to the artifact file from worktree root
  */
case class ReviewArtifact(
  label: String,
  path: String
)

/** Review state for a worktree issue.
  *
  * Represents the state of review artifacts, optionally including
  * review status, phase number, and message.
  *
  * @param status Optional review status (e.g., "awaiting_review", "in_review")
  * @param phase Optional phase number associated with review
  * @param message Optional message about review state
  * @param artifacts List of artifacts available for review (required field)
  */
case class ReviewState(
  status: Option[String],
  phase: Option[Int],
  message: Option[String],
  artifacts: List[ReviewArtifact]
)
