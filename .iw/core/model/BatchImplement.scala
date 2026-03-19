// PURPOSE: Pure decision functions for batch implementation orchestration
// PURPOSE: Given workflow state, determines the next action to take (merge, retry, fail)

package iw.core.model

/** Possible outcomes after inspecting the current phase status. */
enum PhaseOutcome:
  case MergePR
  case MarkDone
  case Recover(prompt: String)
  case Fail(reason: String)

/** Decision logic for the batch-implement loop.
  *
  * Given workflow state, determines the next action (merge, recover, or fail)
  * without performing any I/O.
  */
object BatchImplement:

  /** Map a review-state status string to the next action to take.
    *
    * @param status Raw status string from review-state.json
    * @return PhaseOutcome describing what the orchestrator should do next
    */
  def decideOutcome(status: String): PhaseOutcome =
    status match
      case "awaiting_review"      => PhaseOutcome.MergePR
      case "phase_merged"         => PhaseOutcome.MarkDone
      case "all_complete"         => PhaseOutcome.MarkDone
      case "context_ready"        => PhaseOutcome.Recover("Phase context is ready — re-run implementation")
      case "tasks_ready"          => PhaseOutcome.Recover("Phase tasks are ready — re-run implementation")
      case "implementing"         => PhaseOutcome.Recover("Implementation was interrupted — re-run implementation")
      case "refactoring_complete" => PhaseOutcome.Recover("Refactoring is complete — re-run implementation")
      case "review_failed"        => PhaseOutcome.Recover("Code review failed — re-run implementation addressing review feedback")
      case other                  => PhaseOutcome.Fail(s"Unknown status: '$other'")

  /** Return true if the status means the batch loop should stop.
    *
    * @param status Raw status string from review-state.json
    * @return true for terminal statuses, false otherwise
    */
  def isTerminal(status: String): Boolean =
    decideOutcome(status) == PhaseOutcome.MarkDone

  /** Return the phase number of the first unchecked phase, or None if all are complete.
    *
    * @param phases Ordered list of phase index entries
    * @return Phase number of next incomplete phase, or None
    */
  def nextPhase(phases: List[PhaseIndexEntry]): Option[Int] =
    phases.find(!_.isComplete).map(_.phaseNumber)

  /** Map a workflow type string to its short code for use in iw commands.
    *
    * @param workflowType Workflow type from project config
    * @return Right with short code ("ag" or "wf"), or Left with reason if not batch-implementable
    */
  def resolveWorkflowCode(workflowType: Option[String]): Either[String, String] =
    workflowType match
      case Some("agile")      => Right("ag")
      case Some("waterfall")  => Right("wf")
      case Some("diagnostic") => Left("Workflow type 'diagnostic' is not batch-implementable")
      case Some(other)        => Left(s"Unrecognized workflow type: '$other'")
      case None               => Left("No workflow type configured")

  /** Check a phase off in tasks.md content by replacing `- [ ] Phase N:` with `- [x] Phase N:`.
    *
    * @param tasksContent Full content of the tasks.md file
    * @param phaseNumber Phase number to mark as complete
    * @return Right with updated content, or Left with reason if the line was not found or already checked
    */
  def markPhaseComplete(tasksContent: String, phaseNumber: Int): Either[String, String] =
    val uncheckedPattern = s"^(\\s*-\\s+)\\[ \\](\\s+Phase\\s+$phaseNumber:)".r
    val alreadyCheckedPattern = s"^\\s*-\\s+\\[[xX]\\]\\s+Phase\\s+$phaseNumber:".r
    val lines = tasksContent.split("\n", -1).toList

    if lines.exists(alreadyCheckedPattern.findFirstIn(_).isDefined) then
      Left(s"Phase $phaseNumber is already marked complete")
    else if !lines.exists(uncheckedPattern.findFirstIn(_).isDefined) then
      Left(s"Phase $phaseNumber not found in tasks content")
    else
      Right(lines.map(uncheckedPattern.replaceFirstIn(_, "$1[x]$2")).mkString("\n"))
