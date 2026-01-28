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
      "localhost",
      None
    )

    assert(html.contains("hx-swap-oob=\"afterbegin:#worktree-list\""), "should use afterbegin when no predecessor")
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
      "localhost",
      List("IW-2")  // IW-2 exists (will be deleted), IW-1 is new
    )

    // Should contain addition OOB for IW-1 (IW-1 < IW-2, so no predecessor)
    assert(html.contains("hx-swap-oob=\"afterbegin:#worktree-list\""), "IW-1 should insert at beginning (before IW-2)")
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
      "localhost",
      List.empty
    )

    assertEquals(html, "")

  // Tests for findPredecessor helper function

  test("findPredecessor returns None for empty list"):
    val result = WorktreeListSync.findPredecessor("IW-100", List.empty)
    assertEquals(result, None)

  test("findPredecessor returns None when new ID should be first"):
    val result = WorktreeListSync.findPredecessor("IW-050", List("IW-100", "IW-200"))
    assertEquals(result, None)

  test("findPredecessor returns correct predecessor for middle insertion"):
    val result = WorktreeListSync.findPredecessor("IW-150", List("IW-100", "IW-200", "IW-300"))
    assertEquals(result, Some("IW-100"))

  test("findPredecessor returns last ID when new ID should be last"):
    val result = WorktreeListSync.findPredecessor("IW-400", List("IW-100", "IW-200", "IW-300"))
    assertEquals(result, Some("IW-300"))

  // Tests for generateAdditionOob with predecessorId parameter

  test("generateAdditionOob with predecessorId = None uses afterbegin"):
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
      "localhost",
      None
    )

    assert(html.contains("hx-swap-oob=\"afterbegin:#worktree-list\""), "should use afterbegin when no predecessor")
    assert(html.contains("id=\"card-IW-123\""), "should contain card ID")

  test("generateAdditionOob with predecessorId = Some uses afterend"):
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
      "localhost",
      Some("IW-100")
    )

    assert(html.contains("hx-swap-oob=\"afterend:#card-IW-100\""), "should use afterend with predecessor ID")
    assert(html.contains("id=\"card-IW-123\""), "should contain card ID")

  // Tests for generateChangesResponse with predecessor calculation

  test("generateChangesResponse: addition inserts at beginning when ID is smallest"):
    val now = Instant.now()
    val registration1 = WorktreeRegistration("IW-050", "/path/1", "github", "team", now, now)
    val registration2 = WorktreeRegistration("IW-100", "/path/2", "github", "team", now, now)
    val registration3 = WorktreeRegistration("IW-200", "/path/3", "github", "team", now, now)
    val issueData1 = IssueData("IW-050", "Issue 50", "Open", None, None, "https://example.com/IW-050", now)
    val cached1 = CachedIssue(issueData1)

    val changes = WorktreeListSync.ListChanges(
      additions = List("IW-050"),
      deletions = List.empty,
      reorders = List.empty
    )

    val registrations = Map(
      "IW-050" -> registration1,
      "IW-100" -> registration2,
      "IW-200" -> registration3
    )

    val issueCache = Map("IW-050" -> cached1)
    val currentIds = List("IW-100", "IW-200")

    val html = WorktreeListSync.generateChangesResponse(
      changes,
      registrations,
      issueCache,
      Map.empty,
      Map.empty,
      Map.empty,
      now,
      "localhost",
      currentIds
    )

    assert(html.contains("hx-swap-oob=\"afterbegin:#worktree-list\""), "should insert at beginning when ID is smallest")

  test("generateChangesResponse: addition inserts in middle at correct position"):
    val now = Instant.now()
    val registration1 = WorktreeRegistration("IW-100", "/path/1", "github", "team", now, now)
    val registration2 = WorktreeRegistration("IW-150", "/path/2", "github", "team", now, now)
    val registration3 = WorktreeRegistration("IW-200", "/path/3", "github", "team", now, now)
    val registration4 = WorktreeRegistration("IW-300", "/path/4", "github", "team", now, now)
    val issueData2 = IssueData("IW-150", "Issue 150", "Open", None, None, "https://example.com/IW-150", now)
    val cached2 = CachedIssue(issueData2)

    val changes = WorktreeListSync.ListChanges(
      additions = List("IW-150"),
      deletions = List.empty,
      reorders = List.empty
    )

    val registrations = Map(
      "IW-100" -> registration1,
      "IW-150" -> registration2,
      "IW-200" -> registration3,
      "IW-300" -> registration4
    )

    val issueCache = Map("IW-150" -> cached2)
    val currentIds = List("IW-100", "IW-200", "IW-300")

    val html = WorktreeListSync.generateChangesResponse(
      changes,
      registrations,
      issueCache,
      Map.empty,
      Map.empty,
      Map.empty,
      now,
      "localhost",
      currentIds
    )

    assert(html.contains("hx-swap-oob=\"afterend:#card-IW-100\""), "should insert after IW-100")

  test("generateChangesResponse: addition inserts at end when ID is largest"):
    val now = Instant.now()
    val registration1 = WorktreeRegistration("IW-100", "/path/1", "github", "team", now, now)
    val registration2 = WorktreeRegistration("IW-200", "/path/2", "github", "team", now, now)
    val registration3 = WorktreeRegistration("IW-300", "/path/3", "github", "team", now, now)
    val issueData3 = IssueData("IW-300", "Issue 300", "Open", None, None, "https://example.com/IW-300", now)
    val cached3 = CachedIssue(issueData3)

    val changes = WorktreeListSync.ListChanges(
      additions = List("IW-300"),
      deletions = List.empty,
      reorders = List.empty
    )

    val registrations = Map(
      "IW-100" -> registration1,
      "IW-200" -> registration2,
      "IW-300" -> registration3
    )

    val issueCache = Map("IW-300" -> cached3)
    val currentIds = List("IW-100", "IW-200")

    val html = WorktreeListSync.generateChangesResponse(
      changes,
      registrations,
      issueCache,
      Map.empty,
      Map.empty,
      Map.empty,
      now,
      "localhost",
      currentIds
    )

    assert(html.contains("hx-swap-oob=\"afterend:#card-IW-200\""), "should insert after IW-200 (last existing card)")

  test("generateChangesResponse: multiple additions each get correct predecessor"):
    val now = Instant.now()
    val registration1 = WorktreeRegistration("IW-050", "/path/1", "github", "team", now, now)
    val registration2 = WorktreeRegistration("IW-100", "/path/2", "github", "team", now, now)
    val registration3 = WorktreeRegistration("IW-200", "/path/3", "github", "team", now, now)
    val registration4 = WorktreeRegistration("IW-300", "/path/4", "github", "team", now, now)
    val issueData1 = IssueData("IW-050", "Issue 50", "Open", None, None, "https://example.com/IW-050", now)
    val issueData3 = IssueData("IW-200", "Issue 200", "Open", None, None, "https://example.com/IW-200", now)
    val cached1 = CachedIssue(issueData1)
    val cached3 = CachedIssue(issueData3)

    val changes = WorktreeListSync.ListChanges(
      additions = List("IW-050", "IW-200"),
      deletions = List.empty,
      reorders = List.empty
    )

    val registrations = Map(
      "IW-050" -> registration1,
      "IW-100" -> registration2,
      "IW-200" -> registration3,
      "IW-300" -> registration4
    )

    val issueCache = Map(
      "IW-050" -> cached1,
      "IW-200" -> cached3
    )
    val currentIds = List("IW-100", "IW-300")

    val html = WorktreeListSync.generateChangesResponse(
      changes,
      registrations,
      issueCache,
      Map.empty,
      Map.empty,
      Map.empty,
      now,
      "localhost",
      currentIds
    )

    assert(html.contains("hx-swap-oob=\"afterbegin:#worktree-list\""), "IW-050 should insert at beginning")
    assert(html.contains("hx-swap-oob=\"afterend:#card-IW-100\""), "IW-200 should insert after IW-100")
