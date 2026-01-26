# Phase 1 Context: Help display

**Issue:** IW-103
**Phase:** 1 - Help display
**Story:** Story 7 - Display help when no arguments provided

## Goals

Implement the help display functionality for the new `iw issue create` command. This establishes the user-facing interface contract and serves as documentation for users learning the command.

## Scope

**In Scope:**
- Create new command script at `.iw/commands/issue-create.scala`
- Implement `showHelp()` function with usage information
- Handle `--help` and `-h` flags (exit 0)
- Handle missing `--title` argument (show help, exit 1)
- Display examples for both title-only and title+description usage

**Out of Scope:**
- Actual issue creation (Phase 2+)
- Argument parsing beyond help detection (Phase 2)
- Prerequisite validation (Phase 3)
- Any tracker-specific logic

## Dependencies

**From Previous Phases:** None (this is Phase 1)

**External Dependencies:**
- `iw.core.Output` for consistent output formatting
- Existing help pattern from `feedback.scala` as reference

## Technical Approach

1. **Command Structure:**
   - Create `.iw/commands/issue-create.scala` as a standalone command
   - The bootstrap shell script will route `iw issue create` to this command
   - Follow the same pattern as `feedback.scala` for help display

2. **Help Flow:**
   ```
   iw issue create           → show help, exit 1
   iw issue create --help    → show help, exit 0
   iw issue create -h        → show help, exit 0
   iw issue create --title X → stub success (Phase 2 implements actual creation)
   ```

3. **Help Text Content:**
   - Usage line: `iw issue create --title "Title" [--description "Details"]`
   - Arguments section explaining each flag
   - Examples section with real-world use cases

## Files to Modify

**New Files:**
- `.iw/commands/issue-create.scala` - Main command script

**No modifications to existing files in this phase.**

## Testing Strategy

**Unit Tests:**
- `showHelp()` produces expected output format
- Help text includes required sections (Usage, Arguments, Examples)

**Integration Tests (BATS E2E):**
- `iw issue create` with no args shows help and exits 1
- `iw issue create --help` shows help and exits 0
- `iw issue create -h` shows help and exits 0
- Help text contains `--title` and `--description` flags

## Acceptance Criteria

From Story 7 Gherkin scenarios:

1. ✅ `./iw issue create` shows usage help text
2. ✅ Help shows examples with `--title` flag
3. ✅ Help shows examples with `--title` and `--description` flags
4. ✅ Command exits with code 1 when run without arguments
5. ✅ `./iw issue create --help` shows same help text
6. ✅ `--help` exits with code 0

## Implementation Notes

- Match the style of `feedback.scala` showHelp() exactly
- Use `println()` directly for help output (consistent with existing commands)
- Header comment should follow the PURPOSE pattern from other commands
- The command should be minimal - just help display for now, with a placeholder for actual implementation
