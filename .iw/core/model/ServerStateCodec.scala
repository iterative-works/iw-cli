// PURPOSE: JSON serialization codecs for server state domain models
// PURPOSE: Provides upickle ReadWriter instances for all cached data types and wire format

package iw.core.model

import upickle.default.*
import java.time.Instant

object ServerStateCodec:
  // JSON serialization for Instant timestamps
  given ReadWriter[Instant] = readwriter[String].bimap[Instant](
    instant => instant.toString,
    str => Instant.parse(str)
  )

  // JSON serialization for domain models
  given ReadWriter[WorktreeRegistration] = macroRW[WorktreeRegistration]
  given ReadWriter[IssueData] = macroRW[IssueData]
  given ReadWriter[CachedIssue] = macroRW[CachedIssue]
  given ReadWriter[PhaseInfo] = macroRW[PhaseInfo]
  given ReadWriter[WorkflowProgress] = macroRW[WorkflowProgress]
  given ReadWriter[CachedProgress] = macroRW[CachedProgress]

  // PRState enum serialization (as string)
  given ReadWriter[PRState] = readwriter[String].bimap[PRState](
    state => state.toString,
    str => PRState.valueOf(str)
  )

  given ReadWriter[PullRequestData] = macroRW[PullRequestData]
  given ReadWriter[CachedPR] = macroRW[CachedPR]

  // Review state serialization
  given ReadWriter[ReviewArtifact] = macroRW[ReviewArtifact]
  given ReadWriter[Display] = macroRW[Display]
  given ReadWriter[Badge] = macroRW[Badge]
  given ReadWriter[TaskList] = macroRW[TaskList]
  given ReadWriter[ReviewState] = macroRW[ReviewState]
  given ReadWriter[CachedReviewState] = macroRW[CachedReviewState]

  /** Wire format for state.json file. */
  case class StateJson(
    worktrees: Map[String, WorktreeRegistration],
    issueCache: Map[String, CachedIssue] = Map.empty,
    progressCache: Map[String, CachedProgress] = Map.empty,
    prCache: Map[String, CachedPR] = Map.empty,
    reviewStateCache: Map[String, CachedReviewState] = Map.empty
  )
  given ReadWriter[StateJson] = macroRW[StateJson]
