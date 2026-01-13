# Phase 1 Tasks: Configure SSH host for Zed links

**Issue:** IW-74
**Phase:** 1 - Configure SSH host for Zed links

## Setup

- [x] [setup] Read existing CaskServer and DashboardService implementation

## Tests

- [x] [test] Add DashboardServiceTest for sshHost parameter in renderDashboard signature
- [x] [test] Add DashboardServiceTest for SSH host input rendering in HTML
- [x] [test] Add CaskServerTest for sshHost query parameter handling
- [x] [test] Add CaskServerTest for default hostname fallback

## Implementation

- [x] [impl] Add sshHost parameter to DashboardService.renderDashboard signature
- [x] [impl] Add CSS styles for SSH host input field
- [x] [impl] Add SSH host input form in dashboard header
- [x] [impl] Modify CaskServer.dashboard() to accept sshHost query parameter
- [x] [impl] Add default hostname resolution using InetAddress.getLocalHost().getHostName()
- [x] [impl] Pass sshHost through to DashboardService.renderDashboard()

## Integration

- [x] [integ] Run all tests to verify existing functionality not broken
- [ ] [integ] Manual verification: visit dashboard, verify input field appears
- [ ] [integ] Manual verification: submit form, verify URL updates with ?sshHost=
