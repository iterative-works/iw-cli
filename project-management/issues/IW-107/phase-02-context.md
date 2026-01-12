# Phase 2 Context: Improved Error Messaging

**Issue:** IW-107
**Phase:** 2 - Improved error messaging
**Story:** Clear error when template genuinely missing

---

## Goals

Improve the error message when the `claude-skill-prompt.md` template file is genuinely missing from the iw-cli installation. Currently, the error just says the file wasn't found - users need context to understand whether:
- This is an installation issue (template should exist)
- What specific path was checked
- What actions they can take to fix it

---

## Scope

### In Scope

- Improve error output in claude-sync.scala when template not found
- Show the actual path that was checked
- Provide actionable suggestions (check installation, reinstall)
- Differentiate between "not in project" (expected) vs "missing from installation" (broken)

### Out of Scope

- Changes to template path resolution (done in Phase 1)
- New validation logic or checks
- Other error cases beyond missing template

---

## Dependencies from Phase 1

The following was implemented in Phase 1 and is available:

1. **Constants.EnvVars.IwCommandsDir**: The environment variable name constant for installation directory
2. **Template path resolution**: Already uses `IW_COMMANDS_DIR` with fallback to `os.pwd`
3. **Test infrastructure**: `.iw/test/claude-sync.bats` with helper functions

The current error handling (lines 19-21 in claude-sync.scala) needs enhancement.

---

## Technical Approach

### Current State

Looking at claude-sync.scala after Phase 1, the error handling likely looks similar to:

```scala
if (!os.exists(promptFile)) {
  Output.error(s"Prompt file not found: $promptFile")
  System.exit(1)
}
```

### Enhancement

Improve to something like:

```scala
if (!os.exists(promptFile)) {
  Output.error(s"Template file not found: $promptFile")
  Output.info("This template is part of the iw-cli installation.")
  Output.info("Suggestions:")
  Output.info("  - Check if IW_HOME is set correctly")
  Output.info("  - Try reinstalling iw-cli")
  if (iwDir.isDefined) {
    Output.info(s"  - Installation directory detected: ${iwDir.get}")
  }
  System.exit(1)
}
```

---

## Files to Modify

1. **`.iw/commands/claude-sync.scala`**
   - Enhance the error handling block for missing template
   - Add contextual information and suggestions

2. **`.iw/test/claude-sync.bats`**
   - Add/update test for error message content
   - Verify suggestions are displayed

---

## Testing Strategy

### E2E Tests (BATS)

1. **Test: Missing template shows installation context**
   - Set up environment with IW_COMMANDS_DIR pointing to a directory without template
   - Run claude-sync
   - Verify output contains:
     - The path that was checked
     - Mention of "installation"
     - Suggestion to check IW_HOME or reinstall

2. **Test: Error includes actionable suggestions**
   - Same setup as above
   - Verify output contains at least one actionable suggestion

---

## Acceptance Criteria

From Story 2 in analysis.md:

- [x] Error message clearly indicates installation issue, not project setup issue
- [x] Error shows exact path where template was expected
- [x] Error suggests actionable next steps
- [x] Exit code remains non-zero (1)

---

## Phase Status: Complete
