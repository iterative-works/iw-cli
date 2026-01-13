# Phase 1 Tasks: Configure SSH host for Zed links

**Issue:** IW-74
**Phase:** 1 - Configure SSH host for Zed links

## Setup

- [ ] [setup] Read existing CaskServer and DashboardService implementation

## Tests

- [ ] [test] Add DashboardServiceTest for sshHost parameter in renderDashboard signature
- [ ] [test] Add DashboardServiceTest for SSH host input rendering in HTML
- [ ] [test] Add CaskServerTest for sshHost query parameter handling
- [ ] [test] Add CaskServerTest for default hostname fallback

## Implementation

- [ ] [impl] Add sshHost parameter to DashboardService.renderDashboard signature
- [ ] [impl] Add CSS styles for SSH host input field
- [ ] [impl] Add SSH host input form in dashboard header
- [ ] [impl] Modify CaskServer.dashboard() to accept sshHost query parameter
- [ ] [impl] Add default hostname resolution using InetAddress.getLocalHost().getHostName()
- [ ] [impl] Pass sshHost through to DashboardService.renderDashboard()

## Integration

- [ ] [integ] Run all tests to verify existing functionality not broken
- [ ] [integ] Manual verification: visit dashboard, verify input field appears
- [ ] [integ] Manual verification: submit form, verify URL updates with ?sshHost=
