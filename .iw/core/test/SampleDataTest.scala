// PURPOSE: Unit tests for SampleData fixture generators
// PURPOSE: Validates that sample fixtures create valid domain objects with diverse scenarios

package iw.tests

import munit.FunSuite
import iw.core.dashboard.domain.*
import java.time.Instant
import iw.core.model.CachedIssue
import iw.core.model.CachedPR
import iw.core.model.Issue
import iw.core.model.IssueData

class SampleDataTest extends FunSuite:

  test("sampleWorktrees returns 5 valid WorktreeRegistrations"):
    val worktrees = SampleData.sampleWorktrees
    assertEquals(worktrees.size, 5)

    // Verify all have required fields populated
    worktrees.foreach { wt =>
      assert(wt.issueId.nonEmpty, "Issue ID should not be empty")
      assert(wt.path.nonEmpty, "Path should not be empty")
      assert(wt.trackerType.nonEmpty, "Tracker type should not be empty")
      assert(wt.team.nonEmpty, "Team should not be empty")
    }

  test("sampleWorktrees includes Linear tracker types"):
    val worktrees = SampleData.sampleWorktrees
    val linearWorktrees = worktrees.filter(_.trackerType == "Linear")
    assert(linearWorktrees.size >= 2, "Should have at least 2 Linear worktrees")

    val issueIds = linearWorktrees.map(_.issueId)
    assert(issueIds.contains("IWLE-123"), "Should include IWLE-123")
    assert(issueIds.contains("IWLE-456"), "Should include IWLE-456")

  test("sampleWorktrees includes GitHub tracker type"):
    val worktrees = SampleData.sampleWorktrees
    val githubWorktrees = worktrees.filter(_.trackerType == "GitHub")
    assert(githubWorktrees.size >= 1, "Should have at least 1 GitHub worktree")

    val issueIds = githubWorktrees.map(_.issueId)
    assert(issueIds.contains("GH-100"), "Should include GH-100")

  test("sampleWorktrees includes YouTrack tracker types"):
    val worktrees = SampleData.sampleWorktrees
    val youtrackWorktrees = worktrees.filter(_.trackerType == "YouTrack")
    assert(youtrackWorktrees.size >= 2, "Should have at least 2 YouTrack worktrees")

    val issueIds = youtrackWorktrees.map(_.issueId)
    assert(issueIds.contains("YT-111"), "Should include YT-111")
    assert(issueIds.contains("YT-222"), "Should include YT-222")

  test("sampleWorktrees have timestamps in the past"):
    val worktrees = SampleData.sampleWorktrees
    val now = Instant.now()

    worktrees.foreach { wt =>
      assert(wt.registeredAt.isBefore(now) || wt.registeredAt.equals(now),
        s"registeredAt should not be in the future for ${wt.issueId}")
      assert(wt.lastSeenAt.isBefore(now) || wt.lastSeenAt.equals(now),
        s"lastSeenAt should not be in the future for ${wt.issueId}")
    }

  test("sampleIssues returns IssueData with various statuses"):
    val issues = SampleData.sampleIssues
    assertEquals(issues.size, 5)

    val statuses = issues.map(_.status).toSet
    assert(statuses.size >= 4, "Should have at least 4 different statuses")

  test("sampleIssues includes at least one issue without assignee"):
    val issues = SampleData.sampleIssues
    val unassignedIssues = issues.filter(_.assignee.isEmpty)
    assert(unassignedIssues.nonEmpty, "Should have at least one issue without assignee")

  test("sampleIssues have valid URLs"):
    val issues = SampleData.sampleIssues
    issues.foreach { issue =>
      assert(issue.url.nonEmpty, s"URL should not be empty for ${issue.id}")
      assert(issue.url.startsWith("http"), s"URL should start with http for ${issue.id}")
    }

  test("sampleCachedIssues include fresh and stale timestamps"):
    val cachedIssues = SampleData.sampleCachedIssues
    val now = Instant.now()

    assert(cachedIssues.size >= 3, "Should have at least 3 cached issues")

    // Check that at least one is valid and at least one is stale
    val hasValid = cachedIssues.exists(ci => CachedIssue.isValid(ci, now))
    val hasStale = cachedIssues.exists(ci => CachedIssue.isStale(ci, now))

    assert(hasValid, "Should have at least one valid cached issue")
    assert(hasStale, "Should have at least one stale cached issue")

  test("sampleCachedIssues isStale returns true for stale samples"):
    val cachedIssues = SampleData.sampleCachedIssues
    val now = Instant.now()

    val staleCachedIssues = cachedIssues.filter(ci => CachedIssue.isStale(ci, now))
    assert(staleCachedIssues.nonEmpty, "Should have at least one stale cached issue")

  test("samplePRs returns PRs with all states"):
    val prs = SampleData.samplePRs
    assert(prs.size >= 3, "Should have at least 3 PRs")

    val states = prs.map(_.state).toSet
    assert(states.contains(PRState.Open), "Should have at least one Open PR")
    assert(states.contains(PRState.Merged), "Should have at least one Merged PR")
    assert(states.contains(PRState.Closed), "Should have at least one Closed PR")

  test("samplePRs have valid URLs"):
    val prs = SampleData.samplePRs
    prs.foreach { pr =>
      assert(pr.url.nonEmpty, s"URL should not be empty for PR #${pr.number}")
      assert(pr.url.startsWith("http"), s"URL should start with http for PR #${pr.number}")
    }

  test("sampleCachedPRs include fresh and stale timestamps"):
    val cachedPRs = SampleData.sampleCachedPRs
    val now = Instant.now()

    assert(cachedPRs.size >= 2, "Should have at least 2 cached PRs")

    // Check that at least one is valid and at least one is stale
    val hasValid = cachedPRs.exists(cpr => CachedPR.isValid(cpr, now))
    val hasStale = cachedPRs.exists(cpr => CachedPR.isStale(cpr, now))

    assert(hasValid, "Should have at least one valid cached PR")
    assert(hasStale, "Should have at least one stale cached PR")

  test("sampleWorkflowProgress covers all completion levels"):
    val progressList = SampleData.sampleWorkflowProgress
    assert(progressList.size >= 4, "Should have at least 4 workflow progress samples")

    val percentages = progressList.map(_.overallPercentage).toSet
    // Check for diversity in completion levels (10%, 40%, 60%, 100%)
    assert(percentages.exists(_ < 20), "Should have low completion sample (~10%)")
    assert(percentages.exists(p => p >= 30 && p < 50), "Should have mid completion sample (~40%)")
    assert(percentages.exists(p => p >= 50 && p < 70), "Should have mid-high completion sample (~60%)")
    assert(percentages.exists(_ == 100), "Should have 100% completion sample")

  test("sampleWorkflowProgress have valid phase structures"):
    val progressList = SampleData.sampleWorkflowProgress
    progressList.foreach { progress =>
      assertEquals(progress.phases.size, progress.totalPhases,
        s"phases list size should match totalPhases")
      assert(progress.overallTotal >= progress.overallCompleted,
        s"overallTotal should be >= overallCompleted")
    }

  test("sampleCachedProgress include valid filesMtime"):
    val cachedProgressList = SampleData.sampleCachedProgress
    assert(cachedProgressList.size >= 3, "Should have at least 3 cached progress samples")

    cachedProgressList.foreach { cp =>
      assert(cp.filesMtime.nonEmpty, "filesMtime should not be empty")
      cp.filesMtime.values.foreach { mtime =>
        assert(mtime > 0, "mtime should be positive")
      }
    }

  test("sampleReviewStates cover different statuses"):
    val reviewStates = SampleData.sampleReviewStates
    assert(reviewStates.size >= 3, "Should have at least 3 review states")

    val statuses = reviewStates.flatMap(_.status).toSet
    // Check for variety in review statuses
    assert(statuses.nonEmpty, "Should have review states with status field")

  test("sampleReviewStates include both with and without artifacts"):
    val reviewStates = SampleData.sampleReviewStates
    val withArtifacts = reviewStates.filter(_.artifacts.nonEmpty)
    val withoutArtifacts = reviewStates.filter(_.artifacts.isEmpty)

    assert(withArtifacts.nonEmpty, "Should have at least one review state with artifacts")
    assert(withoutArtifacts.nonEmpty, "Should have at least one review state without artifacts")

  test("sampleCachedReviewStates include valid filesMtime"):
    val cachedReviewStates = SampleData.sampleCachedReviewStates
    assert(cachedReviewStates.size >= 2, "Should have at least 2 cached review states")

    cachedReviewStates.foreach { crs =>
      assert(crs.filesMtime.nonEmpty, "filesMtime should not be empty")
      crs.filesMtime.values.foreach { mtime =>
        assert(mtime > 0, "mtime should be positive")
      }
    }
