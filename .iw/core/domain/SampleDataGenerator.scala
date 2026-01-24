// PURPOSE: Generate complete sample ServerState for development and testing
// PURPOSE: Provides deterministic sample data across all cache types with diverse scenarios

package iw.core.domain

import java.time.Instant
import java.time.temporal.ChronoUnit

/** Utility for generating sample ServerState with complete cache data.
  *
  * Creates a consistent set of 5 sample worktrees with:
  * - Multiple tracker types (Linear, GitHub, YouTrack)
  * - Cached issue data for all worktrees
  * - Cached PR data (4 worktrees have PRs, GH-100 has none)
  * - Cached workflow progress (4 worktrees have workflows, YT-111 has none)
  * - Cached review states (4 worktrees have review states, YT-111 has none)
  */
object SampleDataGenerator:

  /** Generate a complete ServerState with sample data for all 5 worktrees.
    *
    * Sample data design:
    * - IWLE-123: Linear, 40% complete, PR#42 Open, in_review
    * - IWLE-456: Linear, 100% complete, PR#45 Merged, ready_to_merge
    * - GH-100: GitHub, 10% complete, no PR, awaiting_review
    * - YT-111: YouTrack, no workflow, PR#1 Closed, no review state
    * - YT-222: YouTrack, 60% complete, PR#5 Open, in_review
    *
    * @return ServerState with all worktrees and populated caches
    */
  def generateSampleState(): ServerState =
    val now = Instant.now()
    val baseTime = System.currentTimeMillis()

    // Create worktrees
    val worktreesMap = Map(
      "IWLE-123" -> WorktreeRegistration(
        issueId = "IWLE-123",
        path = "/tmp/sample-worktree-iwle-123",
        trackerType = "Linear",
        team = "IWLE",
        registeredAt = now.minus(7, ChronoUnit.DAYS),
        lastSeenAt = now.minus(1, ChronoUnit.HOURS)
      ),
      "IWLE-456" -> WorktreeRegistration(
        issueId = "IWLE-456",
        path = "/tmp/sample-worktree-iwle-456",
        trackerType = "Linear",
        team = "IWLE",
        registeredAt = now.minus(5, ChronoUnit.DAYS),
        lastSeenAt = now.minus(30, ChronoUnit.MINUTES)
      ),
      "GH-100" -> WorktreeRegistration(
        issueId = "GH-100",
        path = "/tmp/sample-worktree-gh-100",
        trackerType = "GitHub",
        team = "iw-cli",
        registeredAt = now.minus(3, ChronoUnit.DAYS),
        lastSeenAt = now.minus(2, ChronoUnit.HOURS)
      ),
      "YT-111" -> WorktreeRegistration(
        issueId = "YT-111",
        path = "/tmp/sample-worktree-yt-111",
        trackerType = "YouTrack",
        team = "TEST",
        registeredAt = now.minus(10, ChronoUnit.DAYS),
        lastSeenAt = now.minus(5, ChronoUnit.HOURS)
      ),
      "YT-222" -> WorktreeRegistration(
        issueId = "YT-222",
        path = "/tmp/sample-worktree-yt-222",
        trackerType = "YouTrack",
        team = "TEST",
        registeredAt = now.minus(2, ChronoUnit.DAYS),
        lastSeenAt = now.minus(10, ChronoUnit.MINUTES)
      )
    )

    // Create issue cache (all 5 worktrees)
    val issueCacheMap = Map(
      "IWLE-123" -> CachedIssue(
        data = IssueData(
          id = "IWLE-123",
          title = "Implement dashboard sample data support",
          status = "In Progress",
          assignee = Some("Michal Příhoda"),
          description = Some("Add support for loading sample data in development mode"),
          url = "https://linear.app/iterative-works/issue/IWLE-123",
          fetchedAt = now.minus(1, ChronoUnit.MINUTES)
        ),
        ttlMinutes = 5
      ),
      "IWLE-456" -> CachedIssue(
        data = IssueData(
          id = "IWLE-456",
          title = "Complete phase 1 implementation",
          status = "Done",
          assignee = Some("Jane Smith"),
          description = Some("Finish all tasks for phase 1"),
          url = "https://linear.app/iterative-works/issue/IWLE-456",
          fetchedAt = now.minus(3, ChronoUnit.MINUTES)
        ),
        ttlMinutes = 5
      ),
      "GH-100" -> CachedIssue(
        data = IssueData(
          id = "GH-100",
          title = "Fix dashboard layout bug",
          status = "Backlog",
          assignee = None,
          description = Some("Dashboard layout breaks on mobile devices"),
          url = "https://github.com/iterative-works/iw-cli/issues/100",
          fetchedAt = now.minus(10, ChronoUnit.MINUTES)
        ),
        ttlMinutes = 5
      ),
      "YT-111" -> CachedIssue(
        data = IssueData(
          id = "YT-111",
          title = "Update documentation",
          status = "Under Review",
          assignee = Some("John Doe"),
          description = Some("Update user guide with new features"),
          url = "https://youtrack.example.com/issue/YT-111",
          fetchedAt = now.minus(2, ChronoUnit.HOURS)
        ),
        ttlMinutes = 5
      ),
      "YT-222" -> CachedIssue(
        data = IssueData(
          id = "YT-222",
          title = "Refactor state management",
          status = "Todo",
          assignee = Some("Alice Brown"),
          description = Some("Simplify state management logic"),
          url = "https://youtrack.example.com/issue/YT-222",
          fetchedAt = now.minus(3, ChronoUnit.MINUTES)
        ),
        ttlMinutes = 5
      )
    )

    // Create PR cache (4 worktrees, GH-100 has no PR)
    val prCacheMap = Map(
      "IWLE-123" -> CachedPR(
        pr = PullRequestData(
          url = "https://github.com/iterative-works/iw-cli/pull/42",
          state = PRState.Open,
          number = 42,
          title = "Add dashboard sample data support"
        ),
        fetchedAt = now.minus(30, ChronoUnit.SECONDS),
        ttlMinutes = 2
      ),
      "IWLE-456" -> CachedPR(
        pr = PullRequestData(
          url = "https://github.com/iterative-works/iw-cli/pull/45",
          state = PRState.Merged,
          number = 45,
          title = "Complete phase 1 implementation"
        ),
        fetchedAt = now.minus(90, ChronoUnit.SECONDS),
        ttlMinutes = 2
      ),
      "YT-111" -> CachedPR(
        pr = PullRequestData(
          url = "https://github.com/iterative-works/iw-cli/pull/1",
          state = PRState.Closed,
          number = 1,
          title = "Update documentation (rejected)"
        ),
        fetchedAt = now.minus(5, ChronoUnit.MINUTES),
        ttlMinutes = 2
      ),
      "YT-222" -> CachedPR(
        pr = PullRequestData(
          url = "https://github.com/iterative-works/iw-cli/pull/5",
          state = PRState.Open,
          number = 5,
          title = "Refactor state management"
        ),
        fetchedAt = now.minus(1, ChronoUnit.MINUTES),
        ttlMinutes = 2
      )
    )

    // Create progress cache (4 worktrees, YT-111 has no workflow)
    val progressCacheMap = Map(
      "IWLE-123" -> CachedProgress(
        progress = WorkflowProgress(
          currentPhase = Some(2),
          totalPhases = 5,
          phases = List(
            PhaseInfo(1, "Setup", "/tmp/sample-worktree-iwle-123/phase-01-tasks.md", 10, 10),
            PhaseInfo(2, "Implementation", "/tmp/sample-worktree-iwle-123/phase-02-tasks.md", 20, 10),
            PhaseInfo(3, "Testing", "/tmp/sample-worktree-iwle-123/phase-03-tasks.md", 15, 0),
            PhaseInfo(4, "Documentation", "/tmp/sample-worktree-iwle-123/phase-04-tasks.md", 10, 0),
            PhaseInfo(5, "Review", "/tmp/sample-worktree-iwle-123/phase-05-tasks.md", 5, 0)
          ),
          overallCompleted = 20,
          overallTotal = 60
        ),
        filesMtime = Map(
          "/tmp/sample-worktree-iwle-123/phase-01-tasks.md" -> (baseTime - 3600000),
          "/tmp/sample-worktree-iwle-123/phase-02-tasks.md" -> (baseTime - 600000),
          "/tmp/sample-worktree-iwle-123/phase-03-tasks.md" -> (baseTime - 7200000),
          "/tmp/sample-worktree-iwle-123/phase-04-tasks.md" -> (baseTime - 7200000),
          "/tmp/sample-worktree-iwle-123/phase-05-tasks.md" -> (baseTime - 7200000)
        )
      ),
      "IWLE-456" -> CachedProgress(
        progress = WorkflowProgress(
          currentPhase = Some(5),
          totalPhases = 5,
          phases = List(
            PhaseInfo(1, "Setup", "/tmp/sample-worktree-iwle-456/phase-01-tasks.md", 8, 8),
            PhaseInfo(2, "Implementation", "/tmp/sample-worktree-iwle-456/phase-02-tasks.md", 15, 15),
            PhaseInfo(3, "Testing", "/tmp/sample-worktree-iwle-456/phase-03-tasks.md", 12, 12),
            PhaseInfo(4, "Documentation", "/tmp/sample-worktree-iwle-456/phase-04-tasks.md", 10, 10),
            PhaseInfo(5, "Review", "/tmp/sample-worktree-iwle-456/phase-05-tasks.md", 5, 5)
          ),
          overallCompleted = 50,
          overallTotal = 50
        ),
        filesMtime = Map(
          "/tmp/sample-worktree-iwle-456/phase-01-tasks.md" -> (baseTime - 86400000),
          "/tmp/sample-worktree-iwle-456/phase-02-tasks.md" -> (baseTime - 86400000),
          "/tmp/sample-worktree-iwle-456/phase-03-tasks.md" -> (baseTime - 86400000),
          "/tmp/sample-worktree-iwle-456/phase-04-tasks.md" -> (baseTime - 86400000),
          "/tmp/sample-worktree-iwle-456/phase-05-tasks.md" -> (baseTime - 86400000)
        )
      ),
      "GH-100" -> CachedProgress(
        progress = WorkflowProgress(
          currentPhase = Some(1),
          totalPhases = 3,
          phases = List(
            PhaseInfo(1, "Analysis", "/tmp/sample-worktree-gh-100/phase-01-tasks.md", 20, 2),
            PhaseInfo(2, "Implementation", "/tmp/sample-worktree-gh-100/phase-02-tasks.md", 30, 0),
            PhaseInfo(3, "Testing", "/tmp/sample-worktree-gh-100/phase-03-tasks.md", 10, 0)
          ),
          overallCompleted = 2,
          overallTotal = 60
        ),
        filesMtime = Map(
          "/tmp/sample-worktree-gh-100/phase-01-tasks.md" -> (baseTime - 1800000),
          "/tmp/sample-worktree-gh-100/phase-02-tasks.md" -> (baseTime - 3600000),
          "/tmp/sample-worktree-gh-100/phase-03-tasks.md" -> (baseTime - 3600000)
        )
      ),
      "YT-222" -> CachedProgress(
        progress = WorkflowProgress(
          currentPhase = Some(3),
          totalPhases = 4,
          phases = List(
            PhaseInfo(1, "Design", "/tmp/sample-worktree-yt-222/phase-01-tasks.md", 10, 10),
            PhaseInfo(2, "Implementation", "/tmp/sample-worktree-yt-222/phase-02-tasks.md", 25, 25),
            PhaseInfo(3, "Testing", "/tmp/sample-worktree-yt-222/phase-03-tasks.md", 20, 5),
            PhaseInfo(4, "Review", "/tmp/sample-worktree-yt-222/phase-04-tasks.md", 5, 0)
          ),
          overallCompleted = 40,
          overallTotal = 60
        ),
        filesMtime = Map(
          "/tmp/sample-worktree-yt-222/phase-01-tasks.md" -> (baseTime - 172800000),
          "/tmp/sample-worktree-yt-222/phase-02-tasks.md" -> (baseTime - 86400000),
          "/tmp/sample-worktree-yt-222/phase-03-tasks.md" -> (baseTime - 3600000),
          "/tmp/sample-worktree-yt-222/phase-04-tasks.md" -> (baseTime - 86400000)
        )
      )
    )

    // Create review state cache (4 worktrees, YT-111 has no review state)
    val reviewStateCacheMap = Map(
      "IWLE-123" -> CachedReviewState(
        state = ReviewState(
          status = Some("in_review"),
          phase = Some(2),
          message = Some("Code review in progress"),
          artifacts = List(
            ReviewArtifact("Analysis", "project-management/issues/IWLE-123/analysis.md"),
            ReviewArtifact("Phase 2 Context", "project-management/issues/IWLE-123/phase-02-context.md")
          )
        ),
        filesMtime = Map(
          "project-management/issues/IWLE-123/analysis.md" -> (baseTime - 3600000),
          "project-management/issues/IWLE-123/phase-02-context.md" -> (baseTime - 1800000)
        )
      ),
      "IWLE-456" -> CachedReviewState(
        state = ReviewState(
          status = Some("ready_to_merge"),
          phase = Some(5),
          message = Some("All reviews complete, ready to merge"),
          artifacts = List(
            ReviewArtifact("Implementation Log", "project-management/issues/IWLE-456/implementation-log.md")
          )
        ),
        filesMtime = Map(
          "project-management/issues/IWLE-456/implementation-log.md" -> (baseTime - 86400000)
        )
      ),
      "GH-100" -> CachedReviewState(
        state = ReviewState(
          status = Some("awaiting_review"),
          phase = Some(1),
          message = Some("Awaiting initial review"),
          artifacts = List.empty
        ),
        filesMtime = Map(
          "project-management/issues/GH-100/review-state.json" -> (baseTime - 600000)
        )
      ),
      "YT-222" -> CachedReviewState(
        state = ReviewState(
          status = Some("in_review"),
          phase = Some(3),
          message = Some("Review artifacts ready"),
          artifacts = List(
            ReviewArtifact("Tasks", "project-management/issues/YT-222/tasks.md"),
            ReviewArtifact("Review Notes", "project-management/issues/YT-222/review.md")
          )
        ),
        filesMtime = Map(
          "project-management/issues/YT-222/tasks.md" -> (baseTime - 7200000),
          "project-management/issues/YT-222/review.md" -> (baseTime - 3600000)
        )
      )
    )

    ServerState(
      worktrees = worktreesMap,
      issueCache = issueCacheMap,
      progressCache = progressCacheMap,
      prCache = prCacheMap,
      reviewStateCache = reviewStateCacheMap
    )
