# Phase 1: Register current worktree to dashboard

**Issue:** IW-63
**Phase:** 1 of 1
**Status:** Not Started

## Goals

Implement the `iw register` command that registers the current working directory (worktree) to the dashboard by:
1. Auto-detecting the issue ID from the current git branch
2. Registering the worktree path with the dashboard server

## Scope

### In Scope
- New command `register.scala` in `.iw/commands/`
- Load project configuration from `.iw/config.conf`
- Get current branch and extract issue ID
- Call `ServerClient.registerWorktree()` with current directory path
- Clear error messages for failure cases
- Success message confirming registration

### Out of Scope
- Command-line arguments (issue ID is auto-detected from branch)
- Creating new branches or worktrees
- Tmux session management
- Any dashboard server changes

## Dependencies

All dependencies already exist:
- `ConfigFileRepository.read()` - load project config
- `GitAdapter.getCurrentBranch()` - get current branch name
- `IssueId.fromBranch()` - parse issue ID from branch name
- `ServerClient.registerWorktree()` - register with dashboard
- `Output` - user feedback utilities

## Technical Approach

Follow the same pattern as `open.scala`:

```scala
@main def register(args: String*): Unit =
  // 1. Load config (fail if missing)
  val config = ConfigFileRepository.read(configPath).getOrElse(exit with error)

  // 2. Get current branch
  val branch = GitAdapter.getCurrentBranch(os.pwd).getOrElse(exit with error)

  // 3. Parse issue ID from branch
  val issueId = IssueId.fromBranch(branch).getOrElse(exit with error)

  // 4. Register worktree (warn if fails, don't error)
  ServerClient.registerWorktree(issueId.value, os.pwd.toString, ...)

  // 5. Output success
  Output.success(s"Registered worktree for ${issueId.value} at ${os.pwd}")
```

## Files to Modify

**New files:**
- `.iw/commands/register.scala` - the new command

**No existing files need modification.**

## Testing Strategy

### Unit Tests
None needed - no new domain logic. All components are already tested.

### E2E Tests (BATS)
New test file `tests/e2e/register.bats`:
1. **Success case**: In worktree with valid branch, `iw register` succeeds
2. **Not in git repo**: Run in `/tmp`, verify error message
3. **Branch without issue ID**: On branch `main`, verify error about missing issue ID
4. **Missing config**: In directory without `.iw/config.conf`, verify error

## Acceptance Criteria

1. `iw register` in worktree with branch `IW-63-foo` outputs: "Registered worktree for IW-63 at [path]"
2. `iw register` outside git repo outputs: "Not in a git repository" (or similar git error)
3. `iw register` on branch `main` outputs: "Cannot extract issue ID from branch 'main'"
4. `iw register` without `.iw/config.conf` outputs: "Cannot read configuration"
5. Dashboard server failure produces warning, not error (exit 0)

## Notes

- This is a simple orchestration command - no new domain logic
- Pattern matches existing commands (`open.scala`, `start.scala`)
- No arguments - everything auto-detected from current directory/branch
