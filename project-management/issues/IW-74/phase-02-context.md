# Phase 2 Context: Open worktree folder in Zed from dashboard

**Issue:** IW-74
**Phase:** 2 - Open worktree folder in Zed from dashboard

## Goals

This phase adds a Zed icon button to each worktree card on the dashboard. Clicking the button opens the worktree folder in Zed using the `zed://ssh://` URL scheme with the SSH host configured in Phase 1.

**Primary deliverable:** Icon button on each worktree card that opens the folder in Zed via SSH remote connection.

## Scope

### In Scope
- Zed icon button on each worktree card (next to PR link)
- Button is an `<a>` tag with `href="zed://ssh://{sshHost}{path}"`
- Zed app icon (16-20px) loaded from GitHub
- Tooltip showing "Open in Zed"
- CSS styling for the icon button

### Out of Scope
- Detection of whether Zed is installed (just link, browser handles scheme)
- Other editors (Zed-only for this issue)
- SSH host validation (already configured in Phase 1)
- Error handling if Zed doesn't open (browser/OS responsibility)

## Dependencies

**From Phase 1:**
- `sshHost` value is available in `DashboardService.renderDashboard()`
- SSH host is passed through to all rendering contexts
- Pattern established: query parameter handling in CaskServer

## Technical Approach

### 1. WorktreeListView Changes
- Add Zed button to worktree card rendering
- Button appears next to PR link (or in similar action area)
- Uses `sshHost` passed from DashboardService

### 2. URL Construction
```scala
val zedUrl = s"zed://ssh://${sshHost}${worktree.path}"
```

### 3. Button HTML Structure
```html
<a href="zed://ssh://dev-server/home/mph/projects/iw-cli-IW-74"
   title="Open in Zed"
   class="zed-button">
  <img src="https://raw.githubusercontent.com/zed-industries/zed/main/crates/zed/resources/app-icon.png"
       alt="Zed"
       width="18" height="18">
</a>
```

### 4. CSS Styling
- Icon button style (same size/pattern as other icon buttons)
- Hover effects
- Alignment with PR link

## Files to Modify

1. **`.iw/core/presentation/WorktreeListView.scala`**
   - Add `sshHost: String` parameter to rendering methods
   - Add Zed button next to PR link in worktree card
   - Construct `zed://ssh://...` URL

2. **`.iw/core/application/DashboardService.scala`**
   - Pass `sshHost` to WorktreeListView when rendering worktree cards

3. **`.iw/core/test/WorktreeListViewTest.scala`**
   - Test Zed button appears in rendered HTML
   - Test URL construction is correct
   - Test button has tooltip

4. **`.iw/core/test/DashboardServiceTest.scala`**
   - Update tests to verify sshHost is passed through

## Testing Strategy

### Unit Tests
1. **WorktreeListView**
   - Verify Zed button appears in worktree card HTML
   - Verify href matches expected `zed://ssh://{host}{path}` format
   - Verify tooltip text is "Open in Zed"
   - Verify icon image src is correct

### Integration Tests
1. **CaskServer/Dashboard**
   - Request dashboard with `?sshHost=test-server`
   - Verify Zed button hrefs use "test-server" as SSH host
   - Verify worktree paths are correctly included in URLs

### Manual Verification
- Visit dashboard with `?sshHost=<your-ssh-alias>`
- Click Zed button on a worktree card
- Verify Zed opens with SSH connection to the folder
- Verify tooltip shows "Open in Zed" on hover

## Acceptance Criteria

- [ ] Zed icon button appears on each worktree card
- [ ] Button is an `<a>` tag linking to `zed://ssh://{sshHost}{path}`
- [ ] Button uses Zed app icon (16-20px)
- [ ] Tooltip shows "Open in Zed" on hover
- [ ] Button href uses the configured SSH host from query parameter
- [ ] All existing tests continue to pass
- [ ] New tests cover the button rendering and URL construction
