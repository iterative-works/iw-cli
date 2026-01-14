# Review Packet: Phase 2 - Open worktree folder in Zed from dashboard

**Issue:** IW-74
**Phase:** 2
**Branch:** IW-74-phase-02

## Goals

Add a Zed icon button to each worktree card on the dashboard that opens the worktree folder in Zed using the `zed://ssh://` URL scheme with the SSH host configured in Phase 1.

## Scenarios

- [x] Zed icon button appears on each worktree card
- [x] Button is an `<a>` tag linking to `zed://ssh://{sshHost}{path}`
- [x] Button uses Zed app icon (18x18px)
- [x] Tooltip shows "Open in Zed" on hover
- [x] Button href uses the configured SSH host from query parameter
- [x] All existing tests continue to pass
- [x] New tests cover the button rendering and URL construction

## Entry Points

Start reviewing from these files:

1. **`.iw/core/WorktreeListView.scala`** (lines 91-105)
   - Added `sshHost: String` parameter to `render()` and `renderWorktreeCard()` methods
   - Added Zed button HTML with icon, tooltip, and `zed://ssh://` URL

2. **`.iw/core/DashboardService.scala`** (line 115)
   - Passes `sshHost` to `WorktreeListView.render()` call

## Diagrams

### Component Flow

```
┌─────────────────────────────────────────────────────────────┐
│ CaskServer                                                  │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ dashboard(sshHost: Option[String])                      │ │
│ │   └─> effectiveSshHost = sshHost.getOrElse(hostname)    │ │
│ └─────────────────────────────────────────────────────────┘ │
└───────────────────────────┬─────────────────────────────────┘
                            │ sshHost
                            ▼
┌─────────────────────────────────────────────────────────────┐
│ DashboardService.renderDashboard(sshHost: String)           │
│   └─> WorktreeListView.render(..., sshHost)                 │
└───────────────────────────┬─────────────────────────────────┘
                            │ sshHost
                            ▼
┌─────────────────────────────────────────────────────────────┐
│ WorktreeListView.render(worktrees, now, sshHost)            │
│   └─> renderWorktreeCard(..., sshHost)                      │
│         └─> <a href="zed://ssh://{sshHost}{path}">          │
└─────────────────────────────────────────────────────────────┘
```

### HTML Structure

```html
<div class="zed-link">
  <a class="zed-button"
     href="zed://ssh://dev-server/home/mph/projects/iw-cli-IW-74"
     title="Open in Zed">
    <img src="https://raw.githubusercontent.com/zed-industries/zed/main/crates/zed/resources/app-icon.png"
         alt="Zed" width="18" height="18">
  </a>
</div>
```

## Test Summary

### Unit Tests (WorktreeListViewTest.scala)

| Test | Status |
|------|--------|
| Zed button renders in worktree card | ✅ |
| Zed button has correct href format (zed://ssh://{host}{path}) | ✅ |
| Zed button has tooltip "Open in Zed" | ✅ |

### Integration Tests (DashboardServiceTest.scala)

| Test | Status |
|------|--------|
| Dashboard includes Zed button with configured SSH host | ✅ |
| Zed button uses correct SSH host for multiple worktrees | ✅ |

## Files Changed

| File | Change Type | Lines |
|------|-------------|-------|
| `.iw/core/WorktreeListView.scala` | Modified | +24 |
| `.iw/core/DashboardService.scala` | Modified | +29 |
| `.iw/core/test/WorktreeListViewTest.scala` | Modified | +83/-38 |
| `.iw/core/test/DashboardServiceTest.scala` | Modified | +38 |

## Key Implementation Details

1. **URL Format**: `zed://ssh://{sshHost}{worktreePath}`
   - Example: `zed://ssh://dev-server/home/mph/projects/iw-cli-IW-74`

2. **Icon**: Uses official Zed app icon from GitHub repository
   - URL: `https://raw.githubusercontent.com/zed-industries/zed/main/crates/zed/resources/app-icon.png`

3. **CSS Styling**:
   - `.zed-link` container with flex layout
   - `.zed-button` with transparent background, subtle border, hover effects
   - Matches existing dashboard button styling patterns

4. **Placement**: Button appears after PR link section in worktree card
