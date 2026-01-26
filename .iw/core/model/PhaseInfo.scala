// PURPOSE: Phase metadata with task completion tracking
// PURPOSE: Domain model for individual phase in agile workflow

package iw.core.model

/** Information about a single phase in the agile workflow.
  *
  * @param phaseNumber Phase number (1-based)
  * @param phaseName Descriptive name of the phase
  * @param taskFilePath Full path to the phase task file (phase-NN-tasks.md)
  * @param totalTasks Total number of tasks in this phase
  * @param completedTasks Number of completed tasks in this phase
  */
case class PhaseInfo(
  phaseNumber: Int,
  phaseName: String,
  taskFilePath: String,
  totalTasks: Int,
  completedTasks: Int
):
  /** True if all tasks in this phase are done.
    * Empty phases (0 tasks) are not considered complete.
    */
  def isComplete: Boolean = totalTasks > 0 && completedTasks == totalTasks

  /** True if phase has some tasks done but not all.
    * Requires at least one task to be started.
    */
  def isInProgress: Boolean = totalTasks > 0 && completedTasks > 0 && completedTasks < totalTasks

  /** True if phase has tasks but none are done yet.
    * Empty phases (0 tasks) are not considered "not started".
    */
  def notStarted: Boolean = totalTasks > 0 && completedTasks == 0

  /** Calculate completion percentage (0-100).
    * Returns 0 for empty phases.
    * Rounds down to nearest integer.
    */
  def progressPercentage: Int =
    if totalTasks == 0 then 0
    else (completedTasks * 100) / totalTasks
