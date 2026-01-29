// PURPOSE: Domain model for review state and artifacts
// PURPOSE: Represents review state data from review-state.json files

package iw.core.model

/** Artifact available for review.
  *
  * @param label Human-readable label for the artifact (e.g., "Analysis", "Phase Context")
  * @param path Relative path to the artifact file from worktree root
  */
case class ReviewArtifact(
  label: String,
  path: String
)

/** Display presentation instructions for status badge rendering.
  *
  * @param text Primary status label shown in the badge
  * @param subtext Optional secondary information shown beneath the badge
  * @param displayType Display category (info, success, warning, error, progress)
  */
case class Display(
  text: String,
  subtext: Option[String],
  displayType: String
)

/** Additional contextual indicator with label and color category.
  *
  * @param label Short text shown on the badge
  * @param badgeType Color category (info, success, warning, error, progress)
  */
case class Badge(
  label: String,
  badgeType: String
)

/** Reference to a markdown file containing task checkboxes.
  *
  * @param label Human-readable name for the task list
  * @param path Relative path from project root to the markdown file
  */
case class TaskList(
  label: String,
  path: String
)

/** Review state for a worktree issue.
  *
  * Represents the state of review artifacts, with optional display
  * instructions, badges, task lists, and message.
  *
  * @param display Optional workflow-controlled presentation instructions
  * @param badges Optional list of contextual badges
  * @param taskLists Optional list of task list file references
  * @param needsAttention Optional flag indicating workflow needs human input
  * @param message Optional prominent notification for the user
  * @param artifacts List of artifacts available for review (required field)
  */
case class ReviewState(
  display: Option[Display],
  badges: Option[List[Badge]],
  taskLists: Option[List[TaskList]],
  needsAttention: Option[Boolean],
  message: Option[String],
  artifacts: List[ReviewArtifact]
)
