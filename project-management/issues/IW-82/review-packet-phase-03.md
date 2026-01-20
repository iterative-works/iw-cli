# Review Packet: Phase 3 - Run server with custom project directory

**Issue:** IW-82
**Phase:** 3
**Branch:** IW-82-phase-03
**Generated:** 2026-01-20

## Goals

Enable running the dashboard server with a custom project directory via `--project=<path>` flag. This allows developers to test UI features in the context of a test project without being physically located in that directory.

## Scenarios

- [ ] Running `./iw dashboard --project=/path/to/project` uses config from specified project
- [ ] Dashboard displays project indicator when custom project is used
- [ ] Auto-prune checks worktree existence relative to project path
- [ ] Combining `--project` with `--state-path` and `--sample-data` works correctly
- [ ] Without `--project` flag, behavior is unchanged (uses `os.pwd`)

## Entry Points

1. **CLI Entry:** `.iw/commands/dashboard.scala:27-29` - `--project` flag parsing
2. **Server Init:** `.iw/core/CaskServer.scala:12` - Constructor with `projectPath` parameter
3. **Dashboard Route:** `.iw/core/CaskServer.scala:36-48` - Uses effectiveProjectPath for config and auto-prune
4. **UI Indicator:** `.iw/core/DashboardService.scala:97-103` - Project path indicator rendering

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                      CLI (dashboard.scala)                       │
│  --project <path>  →  projectPath: Option[String]               │
└─────────────────────────────────────────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────┐
│                     CaskServer Constructor                       │
│  (statePath, port, hosts, projectPath: Option[os.Path], ...)   │
└─────────────────────────────────────────────────────────────────┘
                                  │
                      ┌───────────┴───────────┐
                      ▼                       ▼
┌─────────────────────────────┐   ┌─────────────────────────────┐
│      dashboard() route       │   │    API routes (unchanged)    │
│  • effectiveProjectPath     │   │  Already support ?project=   │
│  • Config from project path │   │  parameter from prior work   │
│  • Auto-prune with path     │   │                              │
└─────────────────────────────┘   └─────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────────────┐
│                      DashboardService                            │
│  renderDashboard(..., projectPath: Option[os.Path])             │
│  → Shows project indicator when projectPath.isDefined           │
└─────────────────────────────────────────────────────────────────┘
```

## Test Summary

| Type | Count | Description |
|------|-------|-------------|
| Unit | 1 | CaskServer constructor accepts projectPath parameter |
| Integration | 1 | GET / uses projectPath for config loading when provided |

## Files Changed

```
M  .iw/commands/dashboard.scala         (+11 lines: --project flag parsing)
M  .iw/core/CaskServer.scala            (+8 lines: projectPath parameter and usage)
M  .iw/core/DashboardService.scala      (+17 lines: projectPath parameter and UI indicator)
M  .iw/core/test/CaskServerTest.scala   (+78 lines: 2 new tests)
```

## Key Changes

### 1. CLI Flag Parsing (dashboard.scala)

```scala
var projectPath: Option[String] = None
// ...
case "--project" if i + 1 < args.length =>
  projectPath = Some(args(i + 1))
  i += 2
```

### 2. CaskServer Constructor (CaskServer.scala:12)

```scala
class CaskServer(statePath: String, port: Int, hosts: Seq[String],
                 projectPath: Option[os.Path], startedAt: Instant)
```

### 3. Dashboard Route (CaskServer.scala:36-48)

```scala
// Use configured project path or fall back to CWD
val effectiveProjectPath = projectPath.getOrElse(os.pwd)

// Auto-prune using project path
val prunedIds = stateService.pruneWorktrees(
  wt => os.exists(os.Path(wt.path, effectiveProjectPath))
)

// Load config from project path
val configPath = effectiveProjectPath / Constants.Paths.IwDir / Constants.Paths.ConfigFileName
```

### 4. UI Indicator (DashboardService.scala:97-103)

```scala
// Project path indicator (only shown when custom project is used)
projectPath.map { path =>
  div(
    cls := "project-indicator",
    "Project: ",
    tag("code")(path.toString)
  )
}
```

## Backward Compatibility

- ✅ When `--project` is not specified, server uses `os.pwd` (unchanged behavior)
- ✅ All existing tests pass (except 2 pre-existing Zed button test failures unrelated to this change)
- ✅ API routes continue to support `?project=` query parameter

## Remaining Work

This phase completes the custom project directory support. The UI indicator is subtle and appears in the dashboard header when a custom project is specified.

---

**Ready for review.**
