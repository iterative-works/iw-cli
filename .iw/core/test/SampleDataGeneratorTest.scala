// PURPOSE: Unit tests for SampleDataGenerator utility
// PURPOSE: Validates that sample state generation is correct, deterministic, and complete

package iw.core.domain

import munit.FunSuite
import iw.core.model.{ServerState, PRState}
import iw.core.dashboard.domain.SampleDataGenerator

class SampleDataGeneratorTest extends FunSuite:

  test("generateSampleState returns ServerState with 5 worktrees"):
    val state = SampleDataGenerator.generateSampleState()
    assertEquals(state.worktrees.size, 5)

    val issueIds = state.worktrees.keys.toSet
    assert(issueIds.contains("IWLE-123"), "Should have IWLE-123")
    assert(issueIds.contains("IWLE-456"), "Should have IWLE-456")
    assert(issueIds.contains("GH-100"), "Should have GH-100")
    assert(issueIds.contains("YT-111"), "Should have YT-111")
    assert(issueIds.contains("YT-222"), "Should have YT-222")

  test("generateSampleState uses 3 tracker types"):
    val state = SampleDataGenerator.generateSampleState()
    val trackerTypes = state.worktrees.values.map(_.trackerType).toSet

    assert(trackerTypes.contains("Linear"), "Should have Linear tracker")
    assert(trackerTypes.contains("GitHub"), "Should have GitHub tracker")
    assert(trackerTypes.contains("YouTrack"), "Should have YouTrack tracker")

  test("generateSampleState is deterministic"):
    val state1 = SampleDataGenerator.generateSampleState()
    val state2 = SampleDataGenerator.generateSampleState()

    // Worktrees should be identical
    assertEquals(state1.worktrees.size, state2.worktrees.size)
    assertEquals(state1.worktrees.keys.toSet, state2.worktrees.keys.toSet)

  test("issueCache contains entries for all 5 worktrees"):
    val state = SampleDataGenerator.generateSampleState()
    assertEquals(state.issueCache.size, 5)

    val issueIds = state.issueCache.keys.toSet
    assertEquals(issueIds, Set("IWLE-123", "IWLE-456", "GH-100", "YT-111", "YT-222"))

  test("progressCache contains entries for 4 worktrees"):
    val state = SampleDataGenerator.generateSampleState()
    // YT-111 has no workflow according to design
    assertEquals(state.progressCache.size, 4)

    val issueIds = state.progressCache.keys.toSet
    assert(issueIds.contains("IWLE-123"), "Should have progress for IWLE-123")
    assert(issueIds.contains("IWLE-456"), "Should have progress for IWLE-456")
    assert(issueIds.contains("GH-100"), "Should have progress for GH-100")
    assert(issueIds.contains("YT-222"), "Should have progress for YT-222")
    assert(!issueIds.contains("YT-111"), "Should NOT have progress for YT-111")

  test("prCache contains entries for 4 worktrees"):
    val state = SampleDataGenerator.generateSampleState()
    // GH-100 has no PR according to design
    assertEquals(state.prCache.size, 4)

    val issueIds = state.prCache.keys.toSet
    assert(issueIds.contains("IWLE-123"), "Should have PR for IWLE-123")
    assert(issueIds.contains("IWLE-456"), "Should have PR for IWLE-456")
    assert(issueIds.contains("YT-111"), "Should have PR for YT-111")
    assert(issueIds.contains("YT-222"), "Should have PR for YT-222")
    assert(!issueIds.contains("GH-100"), "Should NOT have PR for GH-100")

  test("reviewStateCache contains entries for 4 worktrees"):
    val state = SampleDataGenerator.generateSampleState()
    // YT-111 has no review state according to design
    assertEquals(state.reviewStateCache.size, 4)

    val issueIds = state.reviewStateCache.keys.toSet
    assert(issueIds.contains("IWLE-123"), "Should have review state for IWLE-123")
    assert(issueIds.contains("IWLE-456"), "Should have review state for IWLE-456")
    assert(issueIds.contains("GH-100"), "Should have review state for GH-100")
    assert(issueIds.contains("YT-222"), "Should have review state for YT-222")
    assert(!issueIds.contains("YT-111"), "Should NOT have review state for YT-111")

  test("generated data includes missing assignee edge case"):
    val state = SampleDataGenerator.generateSampleState()
    val issues = state.issueCache.values.map(_.data)
    val hasUnassignedIssue = issues.exists(_.assignee.isEmpty)
    assert(hasUnassignedIssue, "Should have at least one issue without assignee (GH-100)")

  test("generated PRs cover all PRState values"):
    val state = SampleDataGenerator.generateSampleState()
    val prStates = state.prCache.values.map(_.pr.state).toSet

    assert(prStates.contains(PRState.Open), "Should have Open PR")
    assert(prStates.contains(PRState.Merged), "Should have Merged PR")
    assert(prStates.contains(PRState.Closed), "Should have Closed PR")

  test("generated state serializes correctly via StateRepository"):
    import iw.core.dashboard.StateRepository
    import iw.tests.Fixtures
    val tempDir = Fixtures.createTempDir("sample-data-test")
    try
      val statePath = (tempDir / "state.json").toString
      val repository = StateRepository(statePath)
      val sampleState = SampleDataGenerator.generateSampleState()

      // Write sample state
      repository.write(sampleState) match
        case Right(_) => // OK
        case Left(err) => fail(s"Failed to write sample state: $err")

      // Read it back
      repository.read() match
        case Right(readState) =>
          assertEquals(readState.worktrees.size, 5, "Should have 5 worktrees")
          assertEquals(readState.issueCache.size, 5, "Should have 5 cached issues")
          assertEquals(readState.progressCache.size, 4, "Should have 4 cached progress entries")
          assertEquals(readState.prCache.size, 4, "Should have 4 cached PRs")
          assertEquals(readState.reviewStateCache.size, 4, "Should have 4 cached review states")
        case Left(err) => fail(s"Failed to read state: $err")
    finally
      os.remove.all(tempDir)
