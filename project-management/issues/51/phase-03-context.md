# Phase 3 Context: Remove numeric-only branch handling

**Issue:** #51
**Phase:** 3 of 3
**Estimated Effort:** 1-2 hours
**Status:** Not Started

## Goals

Simplify the `IssueId` implementation by removing support for bare numeric branches (e.g., `48`, `132`). After this phase:

1. **All branches** must use `TEAM-NNN` format (e.g., `IWCLI-51`)
2. **Bare numeric branches** are rejected with helpful error messages
3. **Code complexity** is reduced by removing `NumericPattern` and `NumericBranchPattern`
4. **Migration guidance** is provided via `iw doctor` for existing numeric branches

This phase completes the transition to unified branch naming across all tracker types.

## Scope

### In Scope

1. **Remove Numeric Patterns from IssueId**
   - Delete `NumericPattern` regex
   - Delete `NumericBranchPattern` regex
   - Remove numeric matching logic from `parse` method
   - Remove numeric matching logic from `fromBranch` method
   - Update error messages to guide users to `TEAM-NNN` format

2. **Update IssueId.parse**
   - Remove backward compatibility case for bare numeric without team prefix
   - Only accept `TEAM-NNN` format or numeric with team prefix context
   - Error message should mention configuring team prefix

3. **Update IssueId.fromBranch**
   - Remove bare numeric branch case (`48`)
   - Remove numeric branch with suffix case (`48-description`)
   - Only accept `TEAM-NNN` and `TEAM-NNN-description` formats
   - Error message should guide to new format

4. **Add Legacy Branch Detection to doctor**
   - Detect bare numeric branches in worktree list
   - Warning message explaining the change
   - Instructions to rename to `TEAM-NNN` format

5. **Update Tests**
   - Remove tests for bare numeric parsing
   - Remove tests for bare numeric branches
   - Remove tests for numeric branch with suffix
   - Update test comments referencing removed patterns
   - Add tests verifying bare numeric is rejected
   - Add test for doctor legacy branch detection

### Out of Scope

- Automated migration command (users rename branches manually)
- Deprecation warnings (hard cutoff approach)
- Support for reading legacy branches (clean break)
- Changes to config or commands (completed in Phases 1-2)

## Dependencies

### Prerequisites from Phase 1

- `ProjectConfiguration` has `teamPrefix: Option[String]` field ✓
- `IssueId.forGitHub(prefix, number)` factory method exists ✓
- Config stores team prefix for GitHub projects ✓
- `iw start` creates `TEAM-NNN` branches ✓

### Prerequisites from Phase 2

- `IssueId.parse(raw, defaultTeam)` accepts optional team prefix ✓
- Commands pass team prefix from config to parse ✓
- Bare numeric input + team prefix → composes `TEAM-NNN` ✓
- Full format input works directly ✓

### What Phase 2 Built

```scala
// Available in IssueId.scala
object IssueId:
  def parse(raw: String, defaultTeam: Option[String] = None): Either[String, IssueId]
  // Now accepts team prefix context to compose TEAM-NNN from numeric input
  
// Commands updated to pass team prefix:
// - issue.scala
// - open.scala
// - rm.scala
// - start.scala
```

### External Dependencies

- Existing `doctor.scala` command and `Check` infrastructure
- Git worktree listing capability (for legacy branch detection)
- Existing test framework

## Technical Approach

### 1. Remove Numeric Patterns from IssueId.scala

**File:** `.iw/core/IssueId.scala`

**Lines to remove:**
- Line 12: `private val NumericPattern = """^[0-9]+$""".r`
- Line 16: `private val NumericBranchPattern = """^([0-9]+)[-_].*""".r`

**Update `parse` method (lines 18-38):**

**Current logic:**
```scala
def parse(raw: String, defaultTeam: Option[String] = None): Either[String, IssueId] =
  val trimmed = raw.trim
  val normalized = trimmed.toUpperCase
  normalized match
    case Pattern() => Right(normalized)
    case _ =>
      trimmed match
        case NumericPattern() =>
          defaultTeam match
            case Some(team) => forGitHub(team, number)
            case None => Right(trimmed) // Backward compatibility
        case _ => Left(s"Invalid issue ID format: $raw (expected: PROJECT-123 or 123)")
```

**New logic:**
```scala
def parse(raw: String, defaultTeam: Option[String] = None): Either[String, IssueId] =
  val trimmed = raw.trim
  val normalized = trimmed.toUpperCase
  normalized match
    case Pattern() => Right(normalized)
    case _ =>
      // Try numeric with team prefix context
      trimmed match
        case NumericPattern() =>
          defaultTeam match
            case Some(team) =>
              trimmed.toIntOption match
                case Some(number) => forGitHub(team, number)
                case None => Left(s"Invalid issue ID format: $raw (expected: TEAM-123)")
            case None =>
              // No longer support bare numeric - guide user to configure team prefix
              Left(s"Invalid issue ID format: $raw (expected: TEAM-123). Configure team prefix with 'iw init' for GitHub projects.")
        case _ => Left(s"Invalid issue ID format: $raw (expected: TEAM-123)")
```

**Wait - after removing NumericPattern, this needs to change:**

```scala
def parse(raw: String, defaultTeam: Option[String] = None): Either[String, IssueId] =
  val trimmed = raw.trim
  val normalized = trimmed.toUpperCase
  normalized match
    case Pattern() => Right(normalized)
    case _ =>
      // If we have team prefix context, try to compose from numeric input
      defaultTeam.flatMap { team =>
        trimmed.toIntOption.map(num => forGitHub(team, num))
      }.getOrElse {
        Left(s"Invalid issue ID format: $raw (expected: TEAM-123). For GitHub projects, configure team prefix with 'iw init'.")
      }
```

**Update `fromBranch` method (lines 50-60):**

**Current logic:**
```scala
def fromBranch(branchName: String): Either[String, IssueId] =
  val normalized = branchName.toUpperCase
  normalized match
    case BranchPattern(issueId) => Right(issueId)
    case _ =>
      branchName match
        case NumericBranchPattern(issueId) => Right(issueId)
        case NumericPattern() => Right(branchName) // Bare numeric
        case _ => Left(s"Cannot extract issue ID from branch '$branchName' (expected: PROJECT-123[-description] or 123[-description])")
```

**New logic:**
```scala
def fromBranch(branchName: String): Either[String, IssueId] =
  val normalized = branchName.toUpperCase
  normalized match
    case BranchPattern(issueId) => Right(issueId)
    case _ =>
      Left(s"Cannot extract issue ID from branch '$branchName' (expected: TEAM-123 or TEAM-123-description). Configure team prefix with 'iw init' for GitHub projects.")
```

**Update `team` extension method (lines 65-70):**

**Current logic:**
```scala
def team: String =
  if issueId.contains("-") then
    issueId.split("-").head
  else
    "" // Numeric GitHub ID has no team
```

**New logic:**
```scala
def team: String =
  // All issue IDs now have TEAM-NNN format
  issueId.split("-").head
```

### 2. Add Legacy Branch Check to doctor

**File:** `.iw/commands/doctor.scala`

**Approach:** Add a new hook-based check rather than modifying core doctor logic.

**Create new file:** `.iw/commands/start.hook-doctor.scala` (or add to existing hook)

**Implementation:**
```scala
// PURPOSE: Doctor check for legacy numeric branches
package doctor

import iw.core.*

object LegacyBranchCheck:
  def check: Check = Check("Legacy branches", { _ =>
    // Get list of all branches from worktrees
    val worktrees = GitAdapter.listWorktrees(os.pwd)
    val branches = worktrees.map(_.branch)
    
    // Check for bare numeric branches
    val numericBranchPattern = """^[0-9]+(-.*)?$""".r
    val legacyBranches = branches.filter {
      case numericBranchPattern() => true
      case _ => false
    }
    
    if legacyBranches.isEmpty then
      CheckResult.Success("No legacy branches found")
    else
      val branchList = legacyBranches.mkString(", ")
      CheckResult.WarningWithHint(
        s"Found ${legacyBranches.size} legacy numeric branch(es): $branchList",
        "Rename to TEAM-NNN format (e.g., '48' → 'IWCLI-48'). Use: git branch -m <old> <new>"
      )
  })
```

**Note:** This requires checking how doctor hooks work. May need to update existing hook file.

### 3. Update Unit Tests

**File:** `.iw/core/test/IssueIdTest.scala`

**Tests to remove:**
- Line 132-135: `test("IssueId.parse accepts numeric GitHub ID 132")`
- Line 137-140: `test("IssueId.parse accepts single digit numeric ID 1")`
- Line 142-145: `test("IssueId.parse accepts multi-digit numeric ID 999")`
- Line 147-150: `test("IssueId.parse trims whitespace from numeric ID")`
- Line 152-155: `test("IssueId.parse does not uppercase numeric IDs")`
- Line 157-160: `test("IssueId.fromBranch extracts numeric prefix with dash separator")`
- Line 162-165: `test("IssueId.fromBranch extracts numeric prefix with underscore separator")`
- Line 167-170: `test("IssueId.fromBranch extracts single digit numeric prefix")`
- Line 177-179: `test("IssueId.team returns empty string for numeric GitHub ID")`
- Line 250-253: `test("IssueId.parse without team prefix accepts numeric input (backward compat)")`

**Tests to update:**
- Line 290-294: `test("IssueId.parse with invalid team prefix...")` - update comment removing `NumericPattern` reference

**Tests to add:**
```scala
test("IssueId.parse rejects bare numeric without team prefix"):
  val result = IssueId.parse("51", None)
  assert(result.isLeft)
  assert(result.left.exists(msg =>
    msg.contains("Invalid") && msg.contains("TEAM-123")
  ))

test("IssueId.parse guides user to configure team prefix for bare numeric"):
  val result = IssueId.parse("51")
  assert(result.isLeft)
  assert(result.left.exists(msg =>
    msg.contains("team prefix") && msg.contains("iw init")
  ))
```

**File:** `.iw/core/test/IssueIdFromBranchTest.scala`

**Tests to remove:**
- Line 65-69: `test("IssueId.fromBranch extracts from numeric branch with numeric suffix (123-456)")`
- Line 86-89: `test("IssueId.fromBranch extracts from bare numeric branch (48)")`

**Test comments to update:**
- Line 66: Remove comment about `NumericBranchPattern`

**Tests to add:**
```scala
test("IssueId.fromBranch rejects bare numeric branch"):
  val result = IssueId.fromBranch("48")
  assert(result.isLeft)
  assert(result.left.exists(msg =>
    msg.contains("Cannot extract") && msg.contains("TEAM-123")
  ))

test("IssueId.fromBranch rejects numeric branch with description"):
  val result = IssueId.fromBranch("51-add-feature")
  assert(result.isLeft)
  assert(result.left.exists(msg =>
    msg.contains("Cannot extract") && msg.contains("TEAM-123")
  ))
```

### 4. Add E2E Tests for doctor

**File:** `.iw/test/doctor.bats`

**Test to add:**
```bash
@test "doctor detects legacy numeric branches" {
  setup_git_repo
  
  # Create a legacy numeric branch
  git branch 48
  git worktree add ../.worktrees/48 48
  
  run iw doctor
  
  # Should warn about legacy branch
  [[ "$output" =~ "Legacy branches" ]]
  [[ "$output" =~ "48" ]]
  [[ "$output" =~ "TEAM-NNN" ]]
  
  # Cleanup
  git worktree remove ../.worktrees/48
  git branch -D 48
}
```

## Testing Strategy

### Unit Tests

1. **IssueId.parse rejection tests:**
   - Bare numeric without team prefix → error with guidance
   - Bare numeric with invalid team prefix → error
   - Non-numeric input → error

2. **IssueId.fromBranch rejection tests:**
   - Bare numeric branch (`48`) → error with guidance
   - Numeric branch with description (`51-feature`) → error
   - Invalid branch formats → error

3. **IssueId.team tests:**
   - All valid IDs now return team prefix (no empty string case)

### Integration Tests

1. **Legacy branch detection:**
   - doctor detects bare numeric branches
   - doctor provides migration instructions
   - doctor passes when no legacy branches exist

### E2E Tests

1. **Error messages guide users:**
   - Commands reject bare numeric input with helpful messages
   - Error messages mention team prefix configuration
   - Error messages show correct format examples

## Files to Modify

### Core Domain

- `.iw/core/IssueId.scala` - Remove patterns, update logic
- `.iw/core/test/IssueIdTest.scala` - Remove/update tests
- `.iw/core/test/IssueIdFromBranchTest.scala` - Remove/update tests

### Commands

- `.iw/commands/start.hook-doctor.scala` (or existing hook) - Add legacy branch check

### E2E Tests

- `.iw/test/doctor.bats` - Add legacy branch detection test

## Acceptance Criteria

### Code Changes

- [ ] `NumericPattern` removed from IssueId.scala
- [ ] `NumericBranchPattern` removed from IssueId.scala
- [ ] `IssueId.parse` rejects bare numeric without team prefix
- [ ] `IssueId.fromBranch` rejects bare numeric branches
- [ ] `IssueId.team` simplified (no empty string case)
- [ ] Error messages guide users to TEAM-NNN format
- [ ] Error messages mention team prefix configuration

### Testing

- [ ] All numeric-only tests removed
- [ ] New rejection tests added and passing
- [ ] Legacy branch detection test added and passing
- [ ] All existing TEAM-NNN tests still pass
- [ ] Unit test suite passes (munit)
- [ ] E2E test suite passes (BATS)

### Documentation

- [ ] Test comments no longer reference removed patterns
- [ ] Code comments updated if they referenced numeric patterns
- [ ] Error messages provide clear migration path

### Behavior Verification

- [ ] `iw issue 51` without team prefix → error with guidance
- [ ] `iw issue IWCLI-51` → works correctly
- [ ] `fromBranch("48")` → error with guidance
- [ ] `fromBranch("IWCLI-48")` → works correctly
- [ ] `iw doctor` detects legacy numeric branches
- [ ] `iw doctor` provides migration instructions

## Risk Assessment

### Low Risk

This phase **removes code** rather than adding it, which reduces complexity and risk:

- Removing patterns simplifies the codebase
- Error messages guide users clearly
- Phase 1 & 2 already established the new format
- Test coverage ensures no regressions

### Potential Issues

1. **Users with existing numeric branches:**
   - Mitigation: Clear error messages guide to solution
   - Mitigation: doctor check provides proactive detection
   - Mitigation: Simple git command to rename branches

2. **Scripts depending on bare numeric format:**
   - Impact: Low (this is a small project, we're the primary users)
   - Mitigation: Error messages make the issue obvious immediately

## Implementation Notes

### Order of Operations

1. **Start with tests** - Add rejection tests first (TDD)
2. **Remove patterns** - Delete NumericPattern and NumericBranchPattern
3. **Update parse** - Remove backward compatibility case
4. **Update fromBranch** - Remove numeric branch cases
5. **Simplify team** - Remove empty string case
6. **Add doctor check** - Detect legacy branches
7. **Clean up tests** - Remove numeric-only tests
8. **Verify behavior** - Run full test suite

### Key Decision: Hard Cutoff

We're using a **hard cutoff** approach (reject immediately) rather than deprecation:

**Rationale:**
- Small project with few users (primarily us)
- Clean break is better than carrying technical debt
- Clear error messages provide immediate guidance
- Simple migration path (single git command)
- Reduces code complexity significantly

### Error Message Guidelines

All error messages should:
- Clearly state what format is expected (`TEAM-123`)
- Mention how to configure team prefix (`iw init`)
- Be consistent across parse and fromBranch
- Be specific to GitHub projects where relevant

**Good error message:**
```
Invalid issue ID format: 51 (expected: TEAM-123). 
For GitHub projects, configure team prefix with 'iw init'.
```

**Bad error message:**
```
Invalid format
```

## Success Criteria

Phase 3 is complete when:

1. ✅ All numeric patterns removed from codebase
2. ✅ Bare numeric input/branches rejected with helpful errors
3. ✅ Legacy branch detection works in doctor
4. ✅ All tests pass (unit + E2E)
5. ✅ No references to removed patterns in comments
6. ✅ Code is simpler than before (fewer LOC, fewer patterns)

---

**Next Steps After Phase 3:**

1. Update implementation-log.md with what was built
2. Mark issue #51 as complete
3. Consider updating project documentation if needed
4. Celebrate simplified codebase! 🎉
