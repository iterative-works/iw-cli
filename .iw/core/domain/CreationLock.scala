// PURPOSE: Domain model for tracking in-progress worktree creation operations
// PURPOSE: Used to prevent concurrent creation attempts for the same issue

package iw.core.domain

import java.time.Instant

case class CreationLock(
  issueId: String,
  startedAt: Instant
)
