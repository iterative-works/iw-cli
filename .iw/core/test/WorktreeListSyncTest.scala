// PURPOSE: Tests for WorktreeListSync diff detection and OOB generation
// PURPOSE: Verifies list synchronization logic for detecting additions, deletions, and reorders
package iw.tests

import iw.core.dashboard.WorktreeListSync
import iw.core.model.{WorktreeRegistration, IssueData, CachedIssue}
import java.time.Instant
import iw.core.model.Issue

class WorktreeListSyncTest extends munit.FunSuite:

  test("detectChanges identifies new worktrees as additions"):
    val oldIds = List("IW-1", "IW-2")
    val newIds = List("IW-1", "IW-2", "IW-3")

    val changes = WorktreeListSync.detectChanges(oldIds, newIds)

    assertEquals(changes.additions, List("IW-3"))
    assertEquals(changes.deletions, List.empty[String])
    assertEquals(changes.reorders, List.empty[String])

  test("detectChanges identifies removed worktrees as deletions"):
    val oldIds = List("IW-1", "IW-2", "IW-3")
    val newIds = List("IW-1", "IW-3")

    val changes = WorktreeListSync.detectChanges(oldIds, newIds)

    assertEquals(changes.additions, List.empty[String])
    assertEquals(changes.deletions, List("IW-2"))
    assertEquals(changes.reorders, List.empty[String])

  test("detectChanges identifies order changes as reorders"):
    val oldIds = List("IW-1", "IW-2", "IW-3")
    val newIds = List("IW-3", "IW-1", "IW-2") // IW-3 moved to front

    val changes = WorktreeListSync.detectChanges(oldIds, newIds)

    assertEquals(changes.additions, List.empty[String])
    assertEquals(changes.deletions, List.empty[String])
    // All three items changed absolute position
    assertEquals(changes.reorders.toSet, Set("IW-1", "IW-2", "IW-3"))

  test("detectChanges returns empty changes for identical lists"):
    val oldIds = List("IW-1", "IW-2", "IW-3")
    val newIds = List("IW-1", "IW-2", "IW-3")

    val changes = WorktreeListSync.detectChanges(oldIds, newIds)

    assertEquals(changes.additions, List.empty[String])
    assertEquals(changes.deletions, List.empty[String])
    assertEquals(changes.reorders, List.empty[String])

  test("detectChanges handles empty old list - all additions"):
    val oldIds = List.empty[String]
    val newIds = List("IW-1", "IW-2", "IW-3")

    val changes = WorktreeListSync.detectChanges(oldIds, newIds)

    assertEquals(changes.additions, List("IW-1", "IW-2", "IW-3"))
    assertEquals(changes.deletions, List.empty[String])
    assertEquals(changes.reorders, List.empty[String])

  test("detectChanges handles empty new list - all deletions"):
    val oldIds = List("IW-1", "IW-2", "IW-3")
    val newIds = List.empty[String]

    val changes = WorktreeListSync.detectChanges(oldIds, newIds)

    assertEquals(changes.additions, List.empty[String])
    assertEquals(changes.deletions, List("IW-1", "IW-2", "IW-3"))
    assertEquals(changes.reorders, List.empty[String])

  test("detectChanges handles mixed changes"):
    val oldIds = List("IW-1", "IW-2", "IW-3")
    val newIds = List("IW-4", "IW-1", "IW-3") // IW-2 deleted, IW-4 added, order same for remaining

    val changes = WorktreeListSync.detectChanges(oldIds, newIds)

    assertEquals(changes.additions, List("IW-4"))
    assertEquals(changes.deletions, List("IW-2"))
    // IW-1 and IW-3 keep their relative order (no reorder)
    assertEquals(changes.reorders, List.empty[String])

  test("generateAdditionOob includes hx-swap-oob attribute"):
    val now = Instant.now()
    val registration = WorktreeRegistration("IW-123", "/path/to/worktree", "github", "team", now, now)
    val issueData = IssueData("IW-123", "Test Issue", "Open", None, None, "https://example.com/IW-123", now)
    val cached = CachedIssue(issueData)

    val html = WorktreeListSync.generateAdditionOob(
      registration,
      Some(cached),
      None,
      None,
      None,
      now,
      "localhost"
    )

    assert(html.contains("hx-swap-oob=\"beforeend:#worktree-list\""), "should contain beforeend swap")
    assert(html.contains("id=\"card-IW-123\""), "should contain card ID")

  test("generateDeletionOob includes hx-swap-oob delete attribute"):
    val html = WorktreeListSync.generateDeletionOob("IW-123")

    assert(html.contains("hx-swap-oob=\"delete\""), "should contain delete swap")
    assert(html.contains("id=\"card-IW-123\""), "should contain card ID")

  test("generateReorderOob generates delete followed by add"):
    val now = Instant.now()
    val registration = WorktreeRegistration("IW-123", "/path/to/worktree", "github", "team", now, now)
    val issueData = IssueData("IW-123", "Test Issue", "Open", None, None, "https://example.com/IW-123", now)
    val cached = CachedIssue(issueData)

    val html = WorktreeListSync.generateReorderOob(
      registration,
      Some(cached),
      None,
      None,
      None,
      0, // position 0 means afterbegin (top)
      now,
      "localhost"
    )

    // Should contain both delete and add
    assert(html.contains("hx-swap-oob=\"delete\""), "should contain delete swap")
    assert(html.contains("hx-swap-oob=\"afterbegin:#worktree-list\""), "should contain afterbegin swap")
    assert(html.contains("id=\"card-IW-123\""), "should contain card ID")

  test("generateChangesResponse combines all OOB swaps"):
    val now = Instant.now()
    val registration1 = WorktreeRegistration("IW-1", "/path/1", "github", "team", now, now)
    val registration2 = WorktreeRegistration("IW-2", "/path/2", "github", "team", now, now)
    val issueData1 = IssueData("IW-1", "Issue 1", "Open", None, None, "https://example.com/IW-1", now)
    val issueData2 = IssueData("IW-2", "Issue 2", "Open", None, None, "https://example.com/IW-2", now)
    val cached1 = CachedIssue(issueData1)
    val cached2 = CachedIssue(issueData2)

    val changes = WorktreeListSync.ListChanges(
      additions = List("IW-1"),
      deletions = List("IW-2"),
      reorders = List.empty
    )

    val registrations = Map(
      "IW-1" -> registration1,
      "IW-2" -> registration2
    )

    val issueCache = Map(
      "IW-1" -> cached1,
      "IW-2" -> cached2
    )

    val html = WorktreeListSync.generateChangesResponse(
      changes,
      registrations,
      issueCache,
      Map.empty,
      Map.empty,
      Map.empty,
      now,
      "localhost"
    )

    // Should contain addition OOB for IW-1
    assert(html.contains("hx-swap-oob=\"beforeend:#worktree-list\""), "should contain addition")
    // Should contain deletion OOB for IW-2
    assert(html.contains("hx-swap-oob=\"delete\""), "should contain deletion")

  test("generateChangesResponse returns empty string for no changes"):
    val changes = WorktreeListSync.ListChanges(
      additions = List.empty,
      deletions = List.empty,
      reorders = List.empty
    )

    val html = WorktreeListSync.generateChangesResponse(
      changes,
      Map.empty,
      Map.empty,
      Map.empty,
      Map.empty,
      Map.empty,
      Instant.now(),
      "localhost"
    )

    assertEquals(html, "")
