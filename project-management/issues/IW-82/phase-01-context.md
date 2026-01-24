# Phase 1 Context: Run server with custom state file

**Issue:** IW-82
**Phase:** 1 of 5
**Story:** Run server with custom state file

## Goals

Enable developers to run the dashboard server with a custom state file path, providing complete isolation from production state. This is the foundation for all dev mode features.

## Scope

### In Scope
- Add `--state-path=<path>` CLI flag to `dashboard.scala`
- Pass custom state path through to `CaskServer.start()`
- Ensure production state at `~/.local/share/iw/server/state.json` is never touched when custom path specified
- Server starts successfully with custom state path
- Browser opens to dashboard as usual

### Out of Scope
- Sample data loading (Phase 2)
- Custom project directory (Phase 3)
- Combined `--dev` flag (Phase 4)
- Full isolation validation (Phase 5)
- Changes to `server.scala` daemon commands (separate scope)

## Dependencies

### From Previous Phases
- None (this is Phase 1)

### Existing Infrastructure
- `CaskServer.start(statePath, port, hosts)` already accepts `statePath` parameter
- `StateRepository(statePath)` already supports custom paths
- `ServerConfigRepository.getOrCreateDefault(configPath)` handles config loading

## Technical Approach

### 1. CLI Argument Parsing in dashboard.scala

The current `dashboard.scala` has a simple `@main def dashboard(): Unit` signature. We need to add optional arguments for dev mode flags.

**Change:**
```scala
@main def dashboard(
  statePath: Option[String] = None  // --state-path=<path>
): Unit =
```

scala-cli automatically maps `--state-path=foo` to the `statePath` parameter.

### 2. State Path Resolution

**Current logic (hardcoded):**
```scala
val serverDir = s"$homeDir/.local/share/iw/server"
val statePath = s"$serverDir/state.json"
```

**New logic (parameterized):**
```scala
val effectiveStatePath = statePath.getOrElse(s"$homeDir/.local/share/iw/server/state.json")
```

### 3. Config Path Handling

For Phase 1, config continues to use production path. Config isolation comes later (or with `--dev` flag).

The config only contains `port` and `hosts` which are safe to share.

### 4. Pass State Path to Server

```scala
startServerAndOpenBrowser(effectiveStatePath, port, url)
```

The `CaskServer.start()` call already accepts the state path, so no changes needed to CaskServer.

## Files to Modify

| File | Change Type | Description |
|------|-------------|-------------|
| `.iw/commands/dashboard.scala` | Modify | Add `statePath` parameter, use it for state path resolution |

**Note:** No changes to `CaskServer.scala` or `StateRepository.scala` - they already support custom paths.

## Testing Strategy

### Unit Tests
- None needed for this phase (no new domain logic)

### Integration Tests
- Test dashboard command with `--state-path=/tmp/test-state.json`
- Verify server uses the custom path
- Verify file is created at custom path when state is written

### Manual Verification
1. Create temp directory: `mkdir -p /tmp/iw-test`
2. Run: `./iw dashboard --state-path=/tmp/iw-test/state.json`
3. Verify server starts and browser opens
4. Register a worktree via the dashboard
5. Verify `/tmp/iw-test/state.json` contains the registered worktree
6. Verify `~/.local/share/iw/server/state.json` is unchanged

### E2E Test (for later phases)
```gherkin
Scenario: Starting server with custom state file
  Given I am in a project directory
  When I run "./iw dashboard --state-path=/tmp/test-state.json"
  Then the server starts successfully
  And the server uses "/tmp/test-state.json" for state persistence
  And my production state at "~/.local/share/iw/server/state.json" is not modified
  And the dashboard opens in my browser
```

## Acceptance Criteria

- [ ] `./iw dashboard --state-path=<path>` starts server with custom state file
- [ ] Production state remains untouched when custom path is used
- [ ] Server persists worktrees to custom path
- [ ] Browser opens to dashboard
- [ ] No `--state-path` flag = uses production path (backward compatible)

## Implementation Notes

### Parent Directory Creation

The `StateRepository` handles creating parent directories when writing state, so no special handling needed for paths like `/tmp/nested/deep/state.json`.

### Error Handling

If the custom state path is invalid (e.g., read-only location), `StateRepository` will fail gracefully when trying to write. The server will still start with empty state, and errors will be logged.

### Relative vs Absolute Paths

Support both:
- `--state-path=/absolute/path/state.json`
- `--state-path=relative/state.json` (relative to CWD)

The path is passed through to `StateRepository` which uses `os.Path` for resolution.

## Risks & Mitigations

| Risk | Mitigation |
|------|------------|
| User forgets custom path and can't find data | Print effective state path on startup |
| Path doesn't exist and parent can't be created | Let StateRepository handle error, log clearly |

## Estimated Effort

4-6 hours (straightforward CLI flag addition)
