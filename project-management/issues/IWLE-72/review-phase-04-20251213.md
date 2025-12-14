# Code Review Results

**Review Context:** Phase 4: Create worktree for issue with tmux session (IWLE-72, Iteration 1/3)
**Files Reviewed:** 12 files
**Skills Applied:** 4 (style, scala3, testing, architecture)
**Timestamp:** 2025-12-13 23:20:00
**Git Context:** Uncommitted changes for Phase 4

---

<review skill="style">

## Style Review

### Critical Issues

None found.

### Warnings

#### W1: Inconsistent adapter naming pattern

**Location:** `.iw/core/Tmux.scala:11`, `.iw/core/GitWorktree.scala:11`

New adapters are named `TmuxAdapter` and `GitWorktreeAdapter`, but existing adapter is `GitAdapter`. Recommend removing "Adapter" suffix for consistency.

#### W2: Outdated file documentation

**Location:** `.iw/core/Process.scala:1-2`

PURPOSE comment only mentions `commandExists` but file now contains `ProcessResult` and `ProcessAdapter.run()`.

#### W3: Missing scaladoc for public domain API

**Location:** `.iw/core/IssueId.scala:8-18`, `.iw/core/WorktreePath.scala:11-18`

Domain value objects lack scaladoc explaining validation rules and usage.

### Suggestions

#### S1: Extract error messages to constants

String literals scattered throughout start.scala. Consider extracting to `StartMessages` object.

</review>

---

<review skill="scala3">

## Scala 3 Review

### Critical Issues

None found.

### Warnings

None found.

### Suggestions

#### S3: Consider opaque types for value objects

IssueId uses case class with private constructor. Scala 3 opaque types provide zero-cost abstraction:

```scala
object IssueId:
  opaque type IssueId = String

  def parse(raw: String): Either[String, IssueId] = ...

  extension (id: IssueId)
    def value: String = id
    def toBranchName: String = id
```

</review>

---

<review skill="testing">

## Testing Review

### Critical Issues

#### C1: Integration tests bypass adapters under test

**Location:**
- `.iw/core/test/TmuxAdapterTest.scala:27-30, 39, 43, 56, 70, 84`
- `.iw/core/test/GitWorktreeAdapterTest.scala:28-35, 41-48, 68, 79, 104, 114`

**Problem:** Tests use direct shell commands (`"tmux ...".!`, `Process(...)`) instead of the adapters they're testing.

**Example:**
```scala
// Current - WRONG
s"tmux new-session -d -s $sessionName".!
try
  assertEquals(TmuxAdapter.sessionExists(sessionName), true)
finally
  s"tmux kill-session -t $sessionName".!

// Should use adapter methods for setup/teardown
TmuxAdapter.createSession(sessionName, workDir)
try
  assertEquals(TmuxAdapter.sessionExists(sessionName), true)
finally
  TmuxAdapter.killSession(sessionName)
```

### Warnings

#### W6: BATS test has confusing logic

**Location:** `.iw/test/start.bats:103-113`

Test named "start fails with invalid issue ID format - lowercase" runs `iwle-123`, ignores result, then runs `not-valid`. Split into two tests.

#### W7: Missing test coverage for error recovery

Cleanup logic when tmux creation fails (lines 82-88 in start.scala) is not tested.

### Suggestions

#### S2: Test name doesn't match behavior

`IssueIdTest.scala:45-49` - "rejects lowercase with no conversion" actually accepts and converts.

#### S4: Add property-based tests

Use ScalaCheck for IssueId validation properties.

#### S5: Add edge case tests for WorktreePath

Test root directory paths, relative vs absolute, special characters.

#### S8: Add full workflow integration test

End-to-end test of complete start workflow.

</review>

---

<review skill="architecture">

## Architecture Review

### Critical Issues

None found.

### Warnings

#### W4: Adapter error handling uses String instead of typed errors

**Location:** `.iw/core/Tmux.scala:17-28`, `.iw/core/GitWorktree.scala:25-38`

Methods return `Either[String, A]` instead of typed error ADTs. Recommend:

```scala
sealed trait TmuxError
object TmuxError:
  case class SessionCreationFailed(stderr: String, exitCode: Int) extends TmuxError
  case class SessionNotFound(name: String) extends TmuxError
```

#### W5: Command layer mixes domain logic with CLI concerns

**Location:** `.iw/commands/start.scala:34-100`

`createWorktreeForIssue` contains significant business logic. Consider extracting to `WorktreeService` following Functional Core principle.

### Suggestions

#### S6: Consider CommandExecutor abstraction

Abstract command execution for easier testing:

```scala
trait CommandExecutor:
  def execute(command: Seq[String]): ProcessResult
```

#### S7: Use Path consistently in command arguments

Create extension method `asCommandArg` for clarity.

</review>

---

## Summary

- **Critical issues:** 1 (must fix before merge)
- **Warnings:** 7 (should fix)
- **Suggestions:** 8 (nice to have)

### By Skill
- style: 0 critical, 3 warnings, 1 suggestion
- scala3: 0 critical, 0 warnings, 1 suggestion
- testing: 1 critical, 2 warnings, 4 suggestions
- architecture: 0 critical, 2 warnings, 2 suggestions

### Priority Actions

**Must Fix (C1):** Update integration tests to use adapters instead of direct shell commands. This is critical because the tests don't actually verify the adapter implementations work correctly.

**Should Fix (W1-W7):** Address naming consistency, documentation, and test logic issues before merge.
