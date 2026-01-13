# Phase 1 Context: Configure SSH host for Zed links

**Issue:** IW-74
**Phase:** 1 - Configure SSH host for Zed links

## Goals

This phase establishes the SSH host configuration mechanism that will be used by Zed buttons in Phase 2. Users accessing the dashboard remotely need to configure which SSH hostname their local Zed editor should use to connect back to the server.

**Primary deliverable:** An input field in the dashboard header where users can set the SSH hostname, with the value persisted via URL query parameter.

## Scope

### In Scope
- Input field in dashboard header for SSH host configuration
- Form submission that reloads page with `?sshHost=<value>` query parameter
- Default value from server's hostname when no query parameter present
- Reading `sshHost` query parameter in CaskServer dashboard route
- Passing `sshHost` value through to dashboard rendering

### Out of Scope
- Zed button on worktree cards (Phase 2)
- Persistent storage of SSH host (URL-based persistence is sufficient)
- Validation of SSH hostname (we trust user input)
- Multiple SSH host configurations per project

## Dependencies

**None** - This is the first phase and establishes infrastructure for Phase 2.

## Technical Approach

### 1. CaskServer Route Changes
- Modify `dashboard()` route in `CaskServer.scala` to accept optional `sshHost` query parameter
- Use `java.net.InetAddress.getLocalHost().getHostName()` for default hostname
- Pass `sshHost` value to `DashboardService.renderDashboard()`

### 2. DashboardService Changes
- Add `sshHost: String` parameter to `renderDashboard()` signature
- Pass through to dashboard HTML template

### 3. Dashboard Header UI
- Add small form with text input in dashboard header (right side)
- Input shows current `sshHost` value
- Form submits to current URL with `?sshHost=` query parameter
- Style to be unobtrusive (small input, subtle styling)

### Implementation Pattern
```scala
// In CaskServer.scala
@cask.get("/")
def dashboard(sshHost: Option[String] = None): cask.Response[String] =
  val effectiveSshHost = sshHost.getOrElse(
    java.net.InetAddress.getLocalHost().getHostName()
  )
  // ... pass effectiveSshHost to DashboardService
```

## Files to Modify

1. **`.iw/core/CaskServer.scala`**
   - Modify `dashboard()` route signature to accept `sshHost` query param
   - Add default hostname resolution
   - Pass `sshHost` to DashboardService

2. **`.iw/core/application/DashboardService.scala`**
   - Add `sshHost: String` parameter to `renderDashboard()`
   - Add SSH host input field to dashboard header HTML
   - Add CSS styles for the input field

3. **`.iw/core/test/CaskServerTest.scala`**
   - Add test for `sshHost` query parameter handling
   - Add test for default hostname fallback

4. **`.iw/core/test/DashboardServiceTest.scala`**
   - Add test for SSH host input rendering
   - Add test for form action URL

## Testing Strategy

### Unit Tests
1. **DashboardService**
   - Verify SSH host input appears in rendered HTML
   - Verify input has correct default value
   - Verify form action points to current page

### Integration Tests
1. **CaskServer**
   - Request `/` without `sshHost` → verify default hostname in HTML
   - Request `/?sshHost=my-server` → verify "my-server" appears in input value

### Manual Verification
- Visit dashboard, see input with server hostname
- Enter custom hostname, submit, verify URL updates
- Verify input retains value after reload

## Acceptance Criteria

- [ ] Input field appears in dashboard header (right side, unobtrusive)
- [ ] Input shows server hostname as default when no query param
- [ ] Submitting form reloads page with `?sshHost=<value>` in URL
- [ ] Input value matches `sshHost` query parameter when present
- [ ] All existing tests continue to pass
- [ ] New tests cover the added functionality
