// PURPOSE: Marks a merged phase complete in the issue's tasks.md index and commits it
// PURPOSE: Shared by phase-advance and phase-merge so the index checkbox tracks the phase_merged transition

package iw.core.commands

import iw.core.model.{BatchImplement, IssueId, PhaseNumber}

/** Catch-up that flips the `- [ ] Phase N:` checkbox in the issue's tasks.md
  * index to `- [x]` once a phase has merged.
  *
  * Both `phase-merge` (happy path) and `phase-advance` (external-merge
  * catch-up) transition review-state to `phase_merged`; this keeps the index in
  * lockstep so the same fact is not recorded in two places that can drift
  * apart.
  *
  * Best-effort: a missing index, an absent phase line, or an already-checked
  * box are non-fatal — the merge has still happened, so we warn (never fail).
  */
object PhaseIndexSync:

  def markPhaseComplete(
      env: CommandEnv,
      issueId: IssueId,
      phaseNumber: PhaseNumber
  ): Unit =
    val tasksPath =
      env.cwd / "project-management" / "issues" / issueId.value / "tasks.md"
    if env.fs.exists(tasksPath) then
      env.fs.read(tasksPath) match
        case Left(err) =>
          warn(env, s"Failed to read tasks.md index: $err")
        case Right(content) =>
          BatchImplement.markPhaseComplete(content, phaseNumber.toInt) match
            case Left(reason) =>
              warn(
                env,
                s"Could not mark phase ${phaseNumber.value} in tasks.md index: $reason"
              )
            case Right(updated) =>
              env.fs.write(tasksPath, updated) match
                case Left(err) =>
                  warn(env, s"Failed to update tasks.md index: $err")
                case Right(()) =>
                  env.git
                    .commitFileWithRetry(
                      tasksPath,
                      s"chore(${issueId.value}): mark phase ${phaseNumber.value} complete in tasks.md",
                      env.cwd
                    )
                    .left
                    .foreach(err =>
                      warn(env, s"Failed to commit tasks.md index update: $err")
                    )

  private def warn(env: CommandEnv, message: String): Unit =
    env.console.err(s"Error: Warning: $message")
