# Story-Driven Analysis: Open issue folder in Zed editor

**Issue:** IW-74
**Created:** 2026-01-13
**Status:** Ready for Implementation
**Classification:** Simple

## Problem Statement

Users access the iw dashboard from a remote machine via SSH. When they want to edit worktree files in Zed, they must manually open Zed and connect to the remote path.

Adding a "Open in Zed" button to each worktree card on the dashboard would streamline the transition from reviewing issues to actively coding. Using Zed's `zed://ssh://` URL scheme, clicking the button opens Zed with the remote folder directly.

## Design Decisions (Resolved)

### Decision 1: Zed URL scheme (not CLI)

**Context:** Dashboard runs on remote server, accessed via SSH. CLI command would launch Zed on server, not user's local machine.

**Decision:** Use `zed://ssh://{sshHost}{worktreePath}` URL scheme.

**Implications:**
- Button is just an `<a href="zed://...">` link
- No server-side process launching needed
- No API endpoint needed
- Browser handles URL scheme → local Zed opens with SSH remote

### Decision 2: SSH host configuration

**Context:** Server doesn't know what SSH hostname the user uses to connect (could be alias, IP, hostname).

**Decision:**
- SSH host comes from query parameter `?sshHost=xxx`
- Default to server's hostname if not provided
- Add small input field in dashboard header to configure hostname
- Input submits as query parameter (page reload with `?sshHost=value`)

### Decision 3: Button placement and style

**Decision:**
- Icon-only button next to PR link in worktree card
- Use Zed app icon from GitHub: `https://raw.githubusercontent.com/zed-industries/zed/main/crates/zed/resources/app-icon.png`
- Always show button (no detection needed - if user doesn't have Zed, link just won't work)

### Decision 4: Scope

**Decision:** Zed-only. Other editors can be added in future issues following the same pattern.

## User Stories

### Story 1: Configure SSH host for Zed links

```gherkin
Feature: Configure SSH host for remote Zed connections
  As a developer accessing the dashboard remotely
  I want to configure the SSH hostname used for Zed links
  So that Zed can connect to the correct remote machine

Scenario: Set SSH host via input field
  Given I am viewing the dashboard
  When I enter "dev-server" in the SSH host input field
  And I submit the form
  Then the page reloads with "?sshHost=dev-server" in the URL
  And all Zed buttons use "dev-server" as the SSH host

Scenario: Default SSH host from server hostname
  Given I am viewing the dashboard without ?sshHost parameter
  When the dashboard renders
  Then the SSH host input shows the server's hostname as default
  And all Zed buttons use the server's hostname
```

**Estimated Effort:** 1-2h
**Complexity:** Straightforward

**Acceptance:**
- Input field in dashboard header (small, unobtrusive)
- Submitting sets `?sshHost=` query parameter
- Default value is server's hostname
- Value persists via URL (bookmarkable)

---

### Story 2: Open worktree folder in Zed from dashboard

```gherkin
Feature: Open worktree in Zed editor
  As a developer viewing the dashboard
  I want to click a button to open the worktree folder in Zed
  So that I can quickly start editing code without manual navigation

Scenario: Click Zed button opens remote folder
  Given a worktree card is displayed for issue "IW-74"
  And the worktree path is "/home/mph/projects/iw-cli-IW-74"
  And the SSH host is configured as "dev-server"
  When I click the Zed icon button
  Then my browser navigates to "zed://ssh://dev-server/home/mph/projects/iw-cli-IW-74"
  And Zed opens with the remote folder connected via SSH
```

**Estimated Effort:** 1-2h
**Complexity:** Straightforward

**Acceptance:**
- Icon button appears on each worktree card (next to PR link)
- Button is an `<a>` tag with `href="zed://ssh://..."`
- Uses Zed app icon (16-20px)
- Tooltip shows "Open in Zed"

## Architectural Sketch

### For Story 1: SSH host configuration

**Presentation Layer:**
- Input field in dashboard header (`DashboardService.scala` styles section)
- Form that submits to current URL with `?sshHost=` parameter
- Read `sshHost` from query params in `CaskServer.scala` dashboard route

**Infrastructure Layer:**
- Get server hostname (Java's `InetAddress.getLocalHost().getHostName()`)

---

### For Story 2: Zed button on worktree cards

**Presentation Layer:**
- Icon button in `WorktreeListView.scala`
- CSS for icon button styling
- Construct `zed://ssh://{sshHost}{path}` URL

**No other layers needed** - it's just HTML rendering.

## Total Estimates

**Story Breakdown:**
- Story 1 (SSH host configuration): 1-2 hours
- Story 2 (Zed button on cards): 1-2 hours

**Total Range:** 2-4 hours

**Confidence:** High

**Reasoning:**
- Pure presentation layer changes
- No server-side logic for launching
- No API endpoints needed
- No error handling complexity (browser handles URL scheme)
- Existing patterns for input fields and icon buttons

## Testing Approach

**Story 1:**
- Unit: Test hostname default logic
- E2E: Visit dashboard, enter hostname, verify URL updates

**Story 2:**
- Unit: Test URL construction (`zed://ssh://{host}{path}`)
- E2E: Visit dashboard with `?sshHost=test`, verify button href is correct

**Note:** Cannot automatically test that Zed actually opens (depends on user's local setup).

## Implementation Sequence

1. **Story 1:** SSH host input field - establishes the configuration mechanism
2. **Story 2:** Zed button on cards - uses the configured host

Stories are small enough to implement together in one session.

---

**Analysis Status:** Ready for Implementation

**Next Steps:**
1. Run `/iterative-works:ag-create-tasks IW-74` to generate implementation phases
2. Run `/iterative-works:ag-implement IW-74` to start implementation
