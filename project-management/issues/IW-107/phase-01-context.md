# Phase 1: Template Path Resolution Fix

**Issue:** IW-107
**Story:** Run claude-sync from any project successfully
**Estimate:** 2-3 hours

## Goals

Fix the `iw claude-sync` command to find the prompt template file from the iw-cli installation directory instead of the current working directory. This enables users to run the command from any project that has iw-cli installed, not just from within the iw-cli repository itself.

## Scope

**In Scope:**
- Add `IwCommandsDir` to `Constants.EnvVars` for consistency
- Modify `claude-sync.scala` to resolve template path from `IW_COMMANDS_DIR` environment variable
- Implement fallback to `os.pwd` when `IW_COMMANDS_DIR` is not set
- E2E tests to verify path resolution behavior

**Out of Scope:**
- Error message improvements (Phase 2)
- Changes to other commands
- Changes to the iw-run bootstrap script

## Dependencies

- No dependencies from previous phases (this is Phase 1)
- Requires existing `IW_COMMANDS_DIR` environment variable set by iw-run bootstrap

## Technical Approach

1. **Add constant to Constants.scala:**
   - Add `IwCommandsDir = "IW_COMMANDS_DIR"` to `Constants.EnvVars`

2. **Modify claude-sync.scala path resolution:**
   - Current: `val promptFile = os.pwd / ".iw" / "scripts" / "claude-skill-prompt.md"`
   - New: Check `IW_COMMANDS_DIR` env var first, fall back to `os.pwd`
   - Path calculation: If `IW_COMMANDS_DIR=/path/.iw/commands`, then template is at `/path/.iw/scripts/claude-skill-prompt.md`

3. **E2E tests:**
   - Test with `IW_COMMANDS_DIR` set (simulating external project usage)
   - Test with `IW_COMMANDS_DIR` unset (fallback behavior)
   - Test template resolution works for existing iw-cli repository usage

## Files to Modify

- `.iw/core/Constants.scala` - Add `IwCommandsDir` to `EnvVars`
- `.iw/commands/claude-sync.scala` - Modify prompt file path resolution (line 17)
- `e2e/claude-sync.bats` - Add/modify E2E tests for path resolution

## Testing Strategy

**E2E Tests (BATS):**
1. Run `iw claude-sync` from iw-cli repo itself (existing usage preserved)
2. Set `IW_COMMANDS_DIR` to iw-cli installation, run from temp directory (external project simulation)
3. Verify fallback when `IW_COMMANDS_DIR` unset works correctly

## Acceptance Criteria

- [ ] `iw claude-sync` works when run from the iw-cli repository itself
- [ ] `iw claude-sync` works when `IW_COMMANDS_DIR` points to iw-cli installation
- [ ] Fallback to `os.pwd` works when `IW_COMMANDS_DIR` is not set
- [ ] Constants.EnvVars contains `IwCommandsDir`
- [ ] E2E tests pass for all scenarios
