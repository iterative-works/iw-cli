// PURPOSE: Unit tests for StateReader read-only state file access
// PURPOSE: Tests reading valid state, missing files, malformed JSON, and empty state

package iw.core.test

import iw.core.adapters.StateReader
import iw.core.model.{ServerState, WorktreeRegistration, CachedIssue, IssueData, CachedProgress, WorkflowProgress, PhaseInfo, CachedPR, PullRequestData, PRState, CachedReviewState, ReviewState}
import java.time.Instant

class StateReaderTest extends munit.FunSuite:

  val fixture = FunFixture[os.Path](
    setup = { test =>
      // Create temp directory for each test
      val tempDir = os.temp.dir()
      tempDir
    },
    teardown = { tempDir =>
      // Clean up temp directory
      if os.exists(tempDir) then
        os.remove.all(tempDir)
    }
  )

  fixture.test("read valid state.json with all cache types populated returns populated ServerState"):
    tempDir =>
      val statePath = tempDir / "state.json"

      // Create a state file with all cache types populated
      val jsonContent = """{
        "worktrees": {
          "IWLE-123": {
            "issueId": "IWLE-123",
            "path": "/path/to/worktree",
            "trackerType": "Linear",
            "team": "IWLE",
            "registeredAt": "2025-01-01T10:00:00Z",
            "lastSeenAt": "2025-01-01T12:00:00Z"
          }
        },
        "issueCache": {
          "IWLE-123": {
            "data": {
              "id": "IWLE-123",
              "title": "Test Issue",
              "status": "In Progress",
              "assignee": "john",
              "description": "Test description",
              "url": "https://example.com/issue/123",
              "fetchedAt": "2025-01-01T10:00:00Z"
            },
            "fetchedAt": "2025-01-01T10:00:00Z"
          }
        },
        "progressCache": {
          "IWLE-123": {
            "progress": {
              "currentPhase": 1,
              "totalPhases": 3,
              "phases": [
                {
                  "phaseNumber": 1,
                  "phaseName": "Domain Layer",
                  "taskFilePath": "/path/to/phase-01-tasks.md",
                  "totalTasks": 10,
                  "completedTasks": 5
                }
              ],
              "overallCompleted": 5,
              "overallTotal": 30
            },
            "filesMtime": {
              "/path/to/phase-01-tasks.md": 1704096000000
            }
          }
        },
        "prCache": {
          "IWLE-123": {
            "pr": {
              "url": "https://github.com/owner/repo/pull/123",
              "state": "Open",
              "number": 123,
              "title": "Test PR"
            },
            "fetchedAt": "2025-01-01T10:00:00Z",
            "ttlMinutes": 60
          }
        },
        "reviewStateCache": {
          "IWLE-123": {
            "state": {
              "display": {
                "text": "Test",
                "subtext": "Test subtext",
                "displayType": "info"
              },
              "badges": [],
              "taskLists": [],
              "needsAttention": false,
              "message": "Test message",
              "artifacts": []
            },
            "filesMtime": {
              "/path/to/review-state.json": 1704096000000
            }
          }
        }
      }"""

      os.write(statePath, jsonContent)

      val result = StateReader.read(statePath.toString)

      assert(result.isRight, s"Expected Right but got Left: ${result.left.getOrElse("")}")
      val state = result.toOption.get
      assertEquals(state.worktrees.size, 1)
      assertEquals(state.issueCache.size, 1)
      assertEquals(state.progressCache.size, 1)
      assertEquals(state.prCache.size, 1)
      assertEquals(state.reviewStateCache.size, 1)

      // Verify actual field values, not just map sizes
      val worktree = state.worktrees("IWLE-123")
      assertEquals(worktree.path, "/path/to/worktree")
      assertEquals(worktree.trackerType, "Linear")
      assertEquals(worktree.team, "IWLE")

      val cachedIssue = state.issueCache("IWLE-123")
      assertEquals(cachedIssue.data.title, "Test Issue")
      assertEquals(cachedIssue.data.status, "In Progress")

      val cachedPR = state.prCache("IWLE-123")
      assertEquals(cachedPR.pr.number, 123)
      assertEquals(cachedPR.pr.state, PRState.Open)

  fixture.test("read from non-existent file returns empty ServerState"):
    tempDir =>
      val statePath = tempDir / "nonexistent.json"

      val result = StateReader.read(statePath.toString)

      assert(result.isRight)
      val state = result.toOption.get
      assertEquals(state.worktrees, Map.empty)
      assertEquals(state.issueCache, Map.empty)
      assertEquals(state.progressCache, Map.empty)
      assertEquals(state.prCache, Map.empty)
      assertEquals(state.reviewStateCache, Map.empty)

  fixture.test("read malformed JSON returns Left with error message"):
    tempDir =>
      val statePath = tempDir / "malformed.json"
      os.write(statePath, "{not valid json")

      val result = StateReader.read(statePath.toString)

      assert(result.isLeft)
      val error = result.left.getOrElse("")
      assert(error.contains("Failed to parse JSON"), s"Error message should mention parsing failure: $error")

  fixture.test("read empty JSON object returns ServerState with empty maps"):
    tempDir =>
      val statePath = tempDir / "empty.json"
      os.write(statePath, """{"worktrees": {}}""")

      val result = StateReader.read(statePath.toString)

      assert(result.isRight)
      val state = result.toOption.get
      assertEquals(state.worktrees, Map.empty)
      assertEquals(state.issueCache, Map.empty)
      assertEquals(state.progressCache, Map.empty)
      assertEquals(state.prCache, Map.empty)
      assertEquals(state.reviewStateCache, Map.empty)
