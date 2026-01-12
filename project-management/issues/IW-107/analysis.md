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

## Technical Risks & Uncertainties

### CLARIFY: Should we add IW_COMMANDS_DIR to Constants.EnvVars?

Currently `Constants.EnvVars` defines environment variables like `LINEAR_API_TOKEN`, `IW_HOOK_CLASSES`, but not `IW_COMMANDS_DIR` or `IW_CORE_DIR`. These are set by the bootstrap script and available at runtime.

**Questions to answer:**
1. Should we add `IW_COMMANDS_DIR` and `IW_CORE_DIR` to `Constants.EnvVars` for consistency?
2. Or is it acceptable to reference these directly as strings in claude-sync.scala?
3. Are there other commands that might need installation directory access in the future?

**Options:**
- **Option A**: Add to Constants.EnvVars
  - Pro: Consistent with existing pattern, centralized, refactorable
  - Pro: Documents the environment contract
  - Con: Requires changing two files instead of one

- **Option B**: Reference directly in claude-sync.scala
  - Pro: Minimal change, only touches affected file
  - Pro: Faster to implement
  - Con: If other commands need this, we'll have duplication

**Recommendation:** Option A - add to Constants.EnvVars for consistency.

---

### CLARIFY: Fallback behavior if IW_COMMANDS_DIR not set?

The `iw-run` script always sets `IW_COMMANDS_DIR`, but should claude-sync have fallback logic for edge cases?

**Questions to answer:**
1. Can `IW_COMMANDS_DIR` ever be unset in normal usage?
2. If unset, should we fall back to checking `os.pwd / ".iw" / "scripts/"` (current behavior)?
3. Or should we fail fast with clear error about broken installation?

**Options:**
- **Option A**: Fail fast if IW_COMMANDS_DIR not set
  - Pro: Clear contract, detects misconfiguration early
  - Pro: Simpler code, no fallback logic
  - Con: Less resilient to environment issues

- **Option B**: Fallback to current behavior (os.pwd)
  - Pro: More resilient, works if run directly via scala-cli
  - Pro: Preserves backward compatibility
  - Con: Masks configuration issues
  - Con: More complex logic

- **Option C**: Try IW_COMMANDS_DIR first, then os.pwd, with clear precedence
  - Pro: Works in all scenarios
  - Con: Most complex option

**Recommendation:** Option A - fail fast for cleaner error handling.

---

### CLARIFY: Testing approach for this fix

This is a path resolution fix that depends on environment setup.

**Options:**
- **Option A**: E2E test only - Tests real-world scenario
- **Option B**: Unit test + E2E test - Tests both logic and integration
- **Option C**: Manual testing only - Fastest to deliver but violates TDD policy

**Recommendation:** Option B - unit + E2E tests per project policy.

---

## Total Estimates

**Story Breakdown:**
- Story 1 (Template path resolution): 2-3 hours
- Story 2 (Improved error messages): 1 hour

**Total Range:** 3-4 hours

**Confidence:** High

---

## Testing Approach

### Story 1: Template path resolution

**Unit Tests:**
- Test path calculation logic from IW_COMMANDS_DIR
- Test fallback behavior (if implemented) when IW_COMMANDS_DIR unset

**E2E Tests (BATS):**
- Run `iw claude-sync` from iw-cli repository (existing usage)
- Create temporary project without local template, verify command works
- Test with corrupted installation (missing template)

### Story 2: Error messaging

**E2E Tests (BATS):**
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

**Analysis Status:** Ready for Review

**Next Steps:**
1. Resolve CLARIFY markers
2. Begin implementation with TDD: Write failing E2E test first
3. Implement fix following TDD cycle
4. Verify in both iw-cli repository and external project manually
