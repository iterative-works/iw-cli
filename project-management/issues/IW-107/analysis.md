# Story-Driven Analysis: claude-sync expects prompt template locally instead of shared IW dir

**Issue:** IW-107
**Created:** 2026-01-12
**Status:** Draft
**Classification:** Simple

## Problem Statement

The `iw claude-sync` command currently looks for the prompt template file (`claude-skill-prompt.md`) in the current working directory at `.iw/scripts/claude-skill-prompt.md`. This is incorrect because when users run `iw claude-sync` from their own projects (not the iw-cli repository itself), this template file doesn't exist locally - it's part of the iw-cli distribution.

**Impact:** The command fails with "Prompt file not found" error when run from any project that has iw-cli installed, even though the template is available in the iw-cli installation directory.

**Root Cause:** Line 17 of `.iw/commands/claude-sync.scala` uses `os.pwd` (current working directory) instead of the installation directory where the command scripts and resources are located.

## User Stories

### Story 1: Run claude-sync from any project successfully

```gherkin
Feature: Claude sync finds template from installation directory
  As a developer using iw-cli in my project
  I want to run "iw claude-sync" successfully
  So that I can generate Claude skills without needing local template files

Scenario: Running claude-sync from a project using iw-cli
  Given I am in a project directory with iw-cli installed
  And the project has no local ".iw/scripts/claude-skill-prompt.md" file
  And the iw-cli installation contains the template at its scripts location
  When I run "iw claude-sync"
  Then the command finds the template from the iw-cli installation directory
  And generates Claude skill files in ".claude/skills/"
  And exits successfully
```

**Estimated Effort:** 2-3h
**Complexity:** Straightforward

**Technical Feasibility:**
This is straightforward because:
- The iw-run bootstrap already sets `IW_COMMANDS_DIR` environment variable pointing to the installation
- We can derive the installation directory from `IW_COMMANDS_DIR` (just go up one level to get `.iw/`, then access `scripts/`)
- Pattern already exists in the codebase for accessing environment variables (see `Constants.EnvVars`)
- Change is localized to one line in claude-sync.scala

**Key Technical Challenge:**
How to access the installation directory from within the Scala command script. The environment variable `IW_COMMANDS_DIR` is available (set by iw-run), so we need to:
1. Read `IW_COMMANDS_DIR` from environment
2. Navigate to sibling `scripts/` directory
3. Access the prompt template file

**Acceptance:**
- `iw claude-sync` works when run from the iw-cli repository itself (current behavior preserved)
- `iw claude-sync` works when run from any other project using iw-cli
- Error message if template genuinely missing is clear and actionable
- No environment variable pollution (use only existing IW_COMMANDS_DIR)

---

### Story 2: Clear error when template genuinely missing

```gherkin
Feature: Helpful error when installation is broken
  As a developer
  I want clear error messages
  So that I can fix installation issues

Scenario: Template missing from installation
  Given I am in any project directory
  And the iw-cli installation is corrupted or incomplete
  And the template file does not exist in the installation directory
  When I run "iw claude-sync"
  Then I see an error message indicating the installation is incomplete
  And the error shows where the template was expected
  And suggests reinstalling or checking IW_HOME
  And exits with non-zero status
```

**Estimated Effort:** 1h
**Complexity:** Straightforward

**Technical Feasibility:**
Very straightforward - just improving the existing error message at lines 19-21 to:
- Show the actual path checked (installation directory, not current directory)
- Suggest potential fixes (reinstall, check IW_HOME, etc.)
- Differentiate between "template not in current project" (expected) vs "template missing from installation" (broken)

**Acceptance:**
- Error message clearly indicates installation issue, not project setup issue
- Error shows exact path where template was expected
- Error suggests actionable next steps
- Exit code remains non-zero (1)

---

## Architectural Sketch

### For Story 1: Template path resolution fix

**Application Layer:**
- `claude-sync.scala` command script
  - Needs to resolve installation directory from environment
  - Calculate path to `scripts/claude-skill-prompt.md` relative to installation

**Infrastructure Layer:**
- Environment variable access: `sys.env.get("IW_COMMANDS_DIR")`
- File system operations: `os.Path` manipulation to navigate from commands dir to scripts dir
- Template file reading: existing `os.read(promptFile)` logic unchanged

**Domain Layer:**
- No domain logic changes needed

**Note on Path Calculation:**
- Current: `os.pwd / ".iw" / "scripts" / "claude-skill-prompt.md"`
- Need: Get IW_COMMANDS_DIR from environment → parent directory → sibling "scripts" directory → template file
- Example: If `IW_COMMANDS_DIR=/path/to/iw-cli/.iw/commands`, then template is at `/path/to/iw-cli/.iw/scripts/claude-skill-prompt.md`

---

### For Story 2: Improved error handling

**Application Layer:**
- `claude-sync.scala` error message formatting
  - Current error shows path being checked
  - Need to add context about installation vs project
  - Add suggestions for fixing

**Presentation Layer:**
- `Output.error()` for primary error message
- `Output.info()` for suggestions/help text

---

## Technical Decisions

### DECISION: Add IW_COMMANDS_DIR to Constants.EnvVars

Add `IW_COMMANDS_DIR` to `Constants.EnvVars` for consistency with existing pattern. This documents the environment contract and centralizes environment variable names.

---

### DECISION: Fallback to os.pwd if IW_COMMANDS_DIR not set

Try `IW_COMMANDS_DIR` first, fall back to `os.pwd / ".iw" / "scripts/"` if not set. This preserves backward compatibility for direct scala-cli usage during development while preferring the installation directory when available.

---

### DECISION: E2E tests only

Use E2E tests (BATS) to verify the fix works in real-world scenarios. The path resolution logic is simple enough that unit tests add little value compared to integration testing.

---

## Total Estimates

**Story Breakdown:**
- Story 1 (Template path resolution): 2-3 hours
- Story 2 (Improved error messages): 1 hour

**Total Range:** 3-4 hours

**Confidence:** High

---

## Testing Approach

### E2E Tests (BATS)

**Story 1: Template path resolution**
- Run `iw claude-sync` from iw-cli repository (existing usage works)
- Simulate running from external project (IW_COMMANDS_DIR points to installation, no local template)
- Verify fallback to os.pwd works when IW_COMMANDS_DIR unset

**Story 2: Error messaging**
- Remove template from test installation
- Verify error output contains installation path and suggestions

---

## Implementation Sequence

**Recommended Story Order:**

1. **Story 1: Template path resolution** - Establishes core fix
2. **Story 2: Error messaging** - Polish on top of working fix

**Single Iteration** - Both stories can be completed in one session (3-4 hours)

---

## Documentation Requirements

- [ ] Update claude-sync.scala PURPOSE comment if significantly changed
- [ ] Add code comment explaining IW_COMMANDS_DIR path resolution
- [ ] Update Constants.scala if adding new environment variable constants

---

**Analysis Status:** Approved

**Next Steps:**
1. Generate tasks: `/iterative-works:ag-create-tasks IW-107`
2. Begin implementation: `/iterative-works:ag-implement IW-107`
