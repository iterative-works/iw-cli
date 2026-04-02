// PURPOSE: Unit tests for WorktreeStatus JSON serialization
// PURPOSE: Verifies round-trip fidelity between server endpoint and client parsing
package iw.tests

import iw.core.model.WorktreeStatus
import munit.FunSuite

class WorktreeStatusTest extends FunSuite:

  test("JSON round-trip with all fields populated"):
    val status = WorktreeStatus(
      issueId = "TEST-123",
      path = "/home/user/project-TEST-123",
      branchName = Some("TEST-123"),
      gitClean = Some(true),
      issueTitle = Some("Add feature"),
      issueStatus = Some("In Progress"),
      issueUrl = Some("https://example.com/TEST-123"),
      prUrl = Some("https://github.com/org/repo/pull/42"),
      prState = Some("Open"),
      prNumber = Some(42),
      reviewDisplay = Some("Implementing"),
      reviewBadges = Some(List("Phase 2", "Tests passing")),
      needsAttention = true,
      currentPhase = Some(2),
      totalPhases = Some(4),
      overallProgress = Some(65)
    )

    val json = upickle.default.write(status)
    val parsed = upickle.default.read[WorktreeStatus](json)

    assertEquals(parsed, status)

  test("JSON round-trip with only required fields"):
    val status = WorktreeStatus(
      issueId = "TEST-456",
      path = "/home/user/project-TEST-456",
      branchName = None,
      gitClean = None,
      issueTitle = None,
      issueStatus = None,
      issueUrl = None,
      prUrl = None,
      prState = None,
      prNumber = None,
      reviewDisplay = None,
      reviewBadges = None,
      needsAttention = false,
      currentPhase = None,
      totalPhases = None,
      overallProgress = None
    )

    val json = upickle.default.write(status)
    val parsed = upickle.default.read[WorktreeStatus](json)

    assertEquals(parsed, status)

  test("JSON contains expected field names"):
    val status = WorktreeStatus(
      issueId = "TEST-789",
      path = "/path",
      branchName = Some("main"),
      gitClean = Some(false),
      issueTitle = None,
      issueStatus = None,
      issueUrl = None,
      prUrl = None,
      prState = None,
      prNumber = None,
      reviewDisplay = None,
      reviewBadges = None,
      needsAttention = false,
      currentPhase = None,
      totalPhases = None,
      overallProgress = None
    )

    val json = upickle.default.write(status)
    val jsonObj = ujson.read(json)

    assertEquals(jsonObj("issueId").str, "TEST-789")
    assertEquals(jsonObj("path").str, "/path")
    assertEquals(jsonObj("needsAttention").bool, false)

    // Option fields are present when Some — verify branchName and gitClean round-trip
    val parsed = upickle.default.read[WorktreeStatus](json)
    assertEquals(parsed.branchName, Some("main"))
    assertEquals(parsed.gitClean, Some(false))

  test("JSON round-trip with empty review badges list"):
    val status = WorktreeStatus(
      issueId = "TEST-000",
      path = "/path",
      branchName = None,
      gitClean = None,
      issueTitle = None,
      issueStatus = None,
      issueUrl = None,
      prUrl = None,
      prState = None,
      prNumber = None,
      reviewDisplay = None,
      reviewBadges = Some(List.empty),
      needsAttention = false,
      currentPhase = None,
      totalPhases = None,
      overallProgress = None
    )

    val json = upickle.default.write(status)
    val parsed = upickle.default.read[WorktreeStatus](json)

    assertEquals(parsed.reviewBadges, Some(List.empty))
