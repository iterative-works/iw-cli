# Phase 4: Combined development mode flag

**Issue:** IW-82
**Phase:** 4 of 5
**Created:** 2026-01-23

## Goals

This phase combines the `--state-path` (Phase 1) and `--sample-data` (Phase 2) flags into a single convenient `--dev` flag that enables a complete development/testing environment with sensible defaults.

When a user runs `./iw dashboard --dev`, the system should:
1. Generate a temporary state file path in `/tmp/iw-dev-<timestamp>/`
2. Also use isolated config in the same temp directory
3. Auto-enable sample data loading
4. Display a visible "DEV MODE" indicator in the dashboard UI
5. Print clear information about what paths are being used

## Scope

### In Scope
- Add `--dev` CLI flag to dashboard command
- Generate timestamped temp directory for isolated state/config
- Auto-enable sample data when `--dev` is used
- Add visible "DEV MODE" banner to dashboard header
- Pass `devMode` flag through to CaskServer and DashboardService

### Out of Scope
- Hot reload (that's a separate feature with `--restart`)
- PID file management (that's for daemon mode)
- Custom project directory (Phase 3 was skipped)

## Dependencies

### From Previous Phases
- **Phase 1**: `--state-path` parameter - already working
- **Phase 2**: `--sample-data` flag and `SampleDataGenerator` - already working

### Technical Dependencies
- CaskServer needs to be told if it's in dev mode (for passing to dashboard)
- DashboardService needs dev mode flag to render the banner
- Isolated config.json needs to be created in temp directory

## Technical Approach

### 1. CLI Flag Parsing (dashboard.scala)

Add `--dev` flag handling:
```scala
var devMode: Boolean = false

case "--dev" =>
  devMode = true
  i += 1
```

When `devMode` is true:
- Generate temp directory: `/tmp/iw-dev-<timestamp>/`
- Set `effectiveStatePath` to `<tempDir>/state.json`
- Set `effectiveConfigPath` to `<tempDir>/config.json`
- Auto-set `sampleData = true`
- Create default config.json in temp dir (port 9876, hosts ["localhost"])

### 2. Config Path Changes (dashboard.scala)

Current code uses hardcoded `configPath`:
```scala
val configPath = s"$serverDir/config.json"
```

Change to support custom config path when in dev mode:
```scala
val effectiveConfigPath = if devMode then s"$tempDir/config.json" else configPath
```

Create default config if it doesn't exist:
```scala
if devMode && !os.exists(os.Path(effectiveConfigPath)) then
  val defaultConfig = ServerConfig(port = 9876, hosts = List("localhost"))
  ServerConfigRepository.write(effectiveConfigPath, defaultConfig)
```

### 3. CaskServer Changes

Add `devMode` parameter to `CaskServer.start()`:
```scala
def start(statePath: String, port: Int = 9876, hosts: Seq[String] = Seq("localhost"), devMode: Boolean = false): Unit
```

Pass `devMode` to CaskServer constructor and store it.

### 4. DashboardService Changes

Add `devMode` parameter to `renderDashboard`:
```scala
def renderDashboard(
  worktrees: List[WorktreeRegistration],
  ...
  devMode: Boolean = false
): String
```

Render "DEV MODE" banner when `devMode` is true:
```html
<div class="dev-mode-banner">DEV MODE</div>
```

### 5. CSS Styling

Add banner styling:
```css
.dev-mode-banner {
  background: #ffc107;
  color: #333;
  text-align: center;
  padding: 8px;
  font-weight: bold;
  font-size: 14px;
  letter-spacing: 1px;
}
```

## Files to Modify

1. `.iw/commands/dashboard.scala` - CLI flag parsing, temp dir generation
2. `.iw/core/CaskServer.scala` - Add devMode parameter, pass to dashboard route
3. `.iw/core/DashboardService.scala` - Add devMode parameter, render banner
4. `.iw/core/ServerConfigRepository.scala` - May need write function (check if exists)

## Testing Strategy

### Unit Tests
- Temp directory generation produces valid, unique paths
- Dev mode auto-enables sample data flag
- DashboardService renders banner when devMode=true
- DashboardService does NOT render banner when devMode=false

### Integration Tests
- CaskServer starts correctly with devMode=true
- Dashboard route passes devMode through correctly
- Temp directory is created with correct structure

### E2E Manual Verification
- `./iw dashboard --dev` starts server with sample data
- Browser shows "DEV MODE" banner
- Console output shows temp paths being used
- Production state file is NOT modified

## Acceptance Criteria

1. `./iw dashboard --dev` starts dashboard with:
   - Temp directory in `/tmp/iw-dev-<timestamp>/`
   - Sample data auto-loaded
   - Isolated config.json created
   - Console output shows all temp paths

2. Dashboard UI shows visible "DEV MODE" banner in header

3. `./iw dashboard` (without --dev) continues to work normally:
   - No banner shown
   - Uses production paths

4. `--dev` can be combined with explicit `--state-path`:
   - Explicit path takes precedence
   - Dev mode indicator still shown
   - Sample data still loaded

## Notes

- The temp directory uses timestamp to allow multiple dev instances
- Config uses default port 9876 and localhost binding
- Banner should be prominent but not intrusive (yellow warning style)
- Console output should clearly indicate isolation paths for transparency
