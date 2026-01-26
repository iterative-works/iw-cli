// PURPOSE: Complete workflow progress across all phases
// PURPOSE: Aggregates phase information and calculates overall completion

package iw.core.model

/** Progress information for entire workflow across all phases.
  *
  * @param currentPhase Phase number currently in progress (1-based), None if no phases
  * @param totalPhases Total number of phases in the workflow
  * @param phases List of all phase details
  * @param overallCompleted Total completed tasks across all phases
  * @param overallTotal Total tasks across all phases
  */
case class WorkflowProgress(
  currentPhase: Option[Int],
  totalPhases: Int,
  phases: List[PhaseInfo],
  overallCompleted: Int,
  overallTotal: Int
):
  /** Get the PhaseInfo for the current phase.
    * Returns None if no current phase or phase not found in list.
    */
  def currentPhaseInfo: Option[PhaseInfo] =
    currentPhase.flatMap(num => phases.find(_.phaseNumber == num))

  /** Calculate overall completion percentage (0-100).
    * Returns 0 if no tasks in workflow.
    * Rounds down to nearest integer.
    */
  def overallPercentage: Int =
    if overallTotal == 0 then 0
    else (overallCompleted * 100) / overallTotal
