// PURPOSE: Application service for managing server state
// PURPOSE: Provides pure functions for state operations, coordinates with repository

package iw.core.application

import iw.core.domain.{ServerState, WorktreeRegistration}
import iw.core.infrastructure.StateRepository

object ServerStateService:
  def load(repo: StateRepository): Either[String, ServerState] =
    repo.read()

  def save(state: ServerState, repo: StateRepository): Either[String, Unit] =
    repo.write(state)

  def listWorktrees(state: ServerState): List[WorktreeRegistration] =
    state.listByActivity
