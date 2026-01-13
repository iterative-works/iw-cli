# Phase 2 Tasks: Open worktree folder in Zed from dashboard

**Issue:** IW-74
**Phase:** 2 - Open worktree folder in Zed from dashboard

## Setup

- [x] [test] Add test for Zed button renders in worktree card
- [x] [test] Add test for Zed button href format (zed://ssh://{host}{path})
- [x] [test] Add test for Zed button tooltip text

## Implementation

- [x] [impl] Add sshHost parameter to WorktreeListView.render method
- [x] [impl] Add sshHost parameter to renderWorktreeCard method
- [x] [impl] Add Zed button HTML to worktree card (next to PR link area)
- [x] [impl] Add CSS styles for Zed icon button

## Integration

- [x] [impl] Pass sshHost from DashboardService to WorktreeListView.render
- [x] [test] Add integration test for Zed button with configured SSH host
- [x] [test] Update existing DashboardServiceTest to pass sshHost to render calls

## Manual Verification

- [ ] [verify] Start dashboard server, verify Zed button appears on worktree cards
- [ ] [verify] Verify button tooltip shows "Open in Zed"
- [ ] [verify] Click button, verify Zed opens with SSH connection (if Zed installed)
