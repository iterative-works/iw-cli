# Phase 3 Context: Run server with custom project directory

**Issue:** IW-82
**Phase:** 3 - Run server with custom project directory
**Created:** 2026-01-20

## Goals

Enable running the dashboard server pointing to a specific project directory, allowing developers to test UI features in the context of a test project without being physically located in that directory.

## Scope

### In Scope

1. **CLI flag** (`--project=<path>`): Accept custom project directory path in dashboard command
2. **Server initialization**: Pass project path to CaskServer for route configuration
3. **Dashboard route**: Load config from specified project instead of `os.pwd`
4. **Auto-prune check**: Use project path for worktree existence check instead of `os.pwd`
5. **UI indicator**: Display current project path in dashboard (subtle, for awareness)

### Out of Scope

- Worktree card routes (already use worktree-specific config from worktree path)
- API routes (already support `project` parameter from Phase 1-style work)
- Sample data (handled in Phase 2)
- Combined `--dev` flag (Phase 4)

## Dependencies

### From Previous Phases

- **Phase 1**: `--state-path` pattern for CLI flag parsing
- **Phase 2**: `--sample-data` flag and argument parsing pattern

### Existing Infrastructure

The codebase already has substantial support:

1. **API routes with project parameter**:
   - `/api/issues/search?project=<path>` - already works
   - `/api/issues/recent?project=<path>` - already works
   - `/api/modal/create-worktree?project=<path>` - already works
   - `/api/worktrees/create` with `projectPath` form field - already works

2. **Dashboard route issues** (current `os.pwd` usage at `CaskServer.scala:38,44`):
   - Auto-prune uses `os.pwd` to check if worktree path exists
   - Config loading uses `os.pwd` for config path construction

3. **CreateWorktreeModal** and **SearchResultsView** already support `projectPath` parameter

## Technical Approach

### 1. CaskServer Constructor Changes

Add `projectPath: Option[os.Path]` to CaskServer constructor:

```scala
class CaskServer(
  statePath: String,
  port: Int,
  hosts: Seq[String],
  projectPath: Option[os.Path],  // NEW
  startedAt: Instant
) extends cask.MainRoutes
```

### 2. Dashboard Route Changes

Modify the `dashboard()` route to use `projectPath` instead of `os.pwd`:

```scala
@cask.get("/")
def dashboard(sshHost: Option[String] = None): cask.Response[String] =
  // Use configured project path or fall back to CWD
  val effectiveProjectPath = projectPath.getOrElse(os.pwd)

  // Auto-prune using project path
  val prunedIds = stateService.pruneWorktrees(
    wt => os.exists(os.Path(wt.path, effectiveProjectPath))
  )

  // Load config from project path
  val configPath = effectiveProjectPath / Constants.Paths.IwDir / Constants.Paths.ConfigFileName
  val config = ConfigFileRepository.read(configPath)
```

### 3. CLI Changes in dashboard.scala

Add `--project` flag parsing:

```scala
var projectPath: Option[String] = None

// In argument parsing loop:
case "--project" if i + 1 < args.length =>
  projectPath = Some(args(i + 1))
  i += 2
```

Pass to CaskServer:

```scala
CaskServer.start(
  effectiveStatePath,
  port,
  projectPath = projectPath.map(p => os.Path(p))
)
```

### 4. UI Indicator

Pass project path to `DashboardService.renderDashboard()` and display a subtle indicator when a custom project is being used (e.g., small badge or text showing "Project: /path/to/project").

## Files to Modify

1. **`.iw/commands/dashboard.scala`**
   - Add `--project` flag parsing
   - Pass project path to `CaskServer.start()`
   - Update usage string

2. **`.iw/core/CaskServer.scala`**
   - Add `projectPath` constructor parameter
   - Update `dashboard()` route to use project path for config loading
   - Update `dashboard()` route to use project path for auto-prune check
   - Update `CaskServer.start()` companion method signature

3. **`.iw/core/application/DashboardService.scala`**
   - Add `projectPath` parameter to `renderDashboard()` (optional, for UI indicator)

4. **`.iw/core/presentation/views/DashboardLayout.scala`** (if exists) or create indicator component
   - Display project path indicator when custom project specified

## Testing Strategy

### Unit Tests

1. **CaskServer construction**: Test that project path parameter is correctly stored
2. **Path resolution**: Test that `projectPath.getOrElse(os.pwd)` logic works correctly

### Integration Tests

1. **Dashboard with custom project**:
   - Start server with `--project=/path/to/test-project`
   - Verify config loaded from test project's `.iw/config.conf`
   - Verify dashboard displays test project context

2. **Worktree creation context**:
   - Create worktree from dashboard with custom project
   - Verify worktree created relative to test project, not CWD

### Manual Verification

1. Run `./iw dashboard --project=/tmp/test-project`
2. Verify dashboard shows test project's worktrees (or empty if new project)
3. Search for issues - should use test project's tracker config
4. Create worktree - should be relative to test project

## Acceptance Criteria

- [x] `./iw dashboard --project=<path>` uses specified project
- [ ] Config loaded from project's `.iw/config.yaml`
- [ ] Issue search uses project's tracker settings
- [ ] Worktree creation relative to project path
- [ ] Dashboard displays correct project context
- [ ] Auto-prune checks worktree existence relative to project path

## Notes for Implementation

1. **Preserve backward compatibility**: When `--project` is not specified, behavior should be identical to current (use `os.pwd`)

2. **Error handling**: If `--project` path doesn't exist or doesn't have `.iw/config.conf`, provide clear error message

3. **Combining with other flags**: Should work with `--state-path` and `--sample-data` from previous phases

4. **Path validation**: Validate that project path is absolute or resolve relative paths consistently
