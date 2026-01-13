# Phase 2 Tasks: Open worktree folder in Zed from dashboard

**Issue:** IW-74
**Phase:** 2 - Open worktree folder in Zed from dashboard

## Setup

- [ ] [test] Add test for Zed button renders in worktree card
- [ ] [test] Add test for Zed button href format (zed://ssh://{host}{path})
- [ ] [test] Add test for Zed button tooltip text

## Implementation

- [ ] [impl] Add sshHost parameter to WorktreeListView.render method
- [ ] [impl] Add sshHost parameter to renderWorktreeCard method
- [ ] [impl] Add Zed button HTML to worktree card (next to PR link area)
- [ ] [impl] Add CSS styles for Zed icon button

## Integration

- [ ] [impl] Pass sshHost from DashboardService to WorktreeListView.render
- [ ] [test] Add integration test for Zed button with configured SSH host
- [ ] [test] Update existing DashboardServiceTest to pass sshHost to render calls

## Manual Verification

- [ ] [verify] Start dashboard server, verify Zed button appears on worktree cards
- [ ] [verify] Verify button tooltip shows "Open in Zed"
- [ ] [verify] Click button, verify Zed opens with SSH connection (if Zed installed)
