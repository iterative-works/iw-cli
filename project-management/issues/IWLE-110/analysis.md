# Story-Driven Analysis: Add host/interface configuration option to dashboard server

**Issue:** IWLE-110
**Created:** 2025-12-21
**Status:** Ready for Implementation
**Classification:** Simple

## Problem Statement

The dashboard server currently binds exclusively to `localhost:9876`, preventing access from other devices on the network. Users who want to access the dashboard remotely (e.g., via Tailscale VPN from mobile devices) cannot do so without setting up port forwarding on their machine.

This limitation reduces the dashboard's utility in remote development scenarios and adds unnecessary infrastructure complexity.

## Design Decisions

The following decisions were made during analysis review:

### DECIDED: Multiple host bindings with `hosts` array

Instead of a single `host` field, use `hosts: ["127.0.0.1", "100.x.y.z"]` to support binding to multiple interfaces simultaneously. This allows binding to both localhost AND Tailscale without exposing on all interfaces (which `0.0.0.0` would do).

**Rationale:** Security - binding only to intended interfaces means a firewall misconfiguration doesn't expose the service publicly.

### DECIDED: Default to `["localhost"]` when field is missing

When reading existing config files without a `hosts` field, default to `["localhost"]` during deserialization without modifying the file.

**Rationale:** Backward compatibility - existing configs continue to work unchanged.

### DECIDED: Health check always uses `localhost`

Regardless of binding configuration, health check connects to `localhost:port`.

**Rationale:** Reliability - localhost is always available when the server is running on the machine.

### DECIDED: Validation rules for host values

Valid values:
- `localhost`, `127.0.0.1`, `::1` (localhost variants)
- `0.0.0.0`, `::` (bind all interfaces)
- Valid IPv4 addresses
- Valid IPv6 addresses

Invalid values are rejected with a clear error message.

**Rationale:** Better UX - catches typos before spawning daemon process.

---

## User Stories

### Story 1: Configure dashboard to bind to multiple interfaces

```gherkin
Feature: Dashboard host configuration
  As a developer using Tailscale
  I want to configure the dashboard to bind to specific interfaces
  So that I can access it from my mobile device without exposing it publicly

Scenario: Configure multiple hosts for localhost and Tailscale
  Given the server is not running
  And the config file at ~/.local/share/iw/server/config.json contains:
    """
    {
      "port": 9876,
      "hosts": ["127.0.0.1", "100.64.1.5"]
    }
    """
  When I run "iw server start"
  Then the server starts successfully
  And I see "Server started on 127.0.0.1:9876, 100.64.1.5:9876"
  And the server accepts connections on localhost
  And the server accepts connections on the Tailscale IP

Scenario: Default to localhost when hosts not specified
  Given the server is not running
  And the config file contains only:
    """
    {
      "port": 9876
    }
    """
  When I run "iw server start"
  Then the server starts successfully
  And I see "Server started on localhost:9876"
  And the server only accepts connections on localhost
```

**Estimated Effort:** 4-5h
**Complexity:** Straightforward

**Technical Feasibility:**
Straightforward configuration extension:
- Add `hosts: Seq[String]` field to `ServerConfig` case class with default `Seq("localhost")`
- Pass hosts through the process spawning chain
- Configure Undertow to add multiple listeners (one per host)
- Update startup messages to show all bound addresses

**Acceptance:**
- Server binds to all configured host interfaces
- Startup message displays all bound addresses
- Default behavior (localhost only) is preserved when hosts not configured
- Config file is backward compatible (missing `hosts` field defaults to `["localhost"]`)

---

### Story 2: Display bound hosts in server status

```gherkin
Feature: Server status shows bound interfaces
  As a developer
  I want to see which interfaces the server is bound to
  So that I know how to access the dashboard

Scenario: Status shows all configured hosts
  Given the server is running with hosts ["127.0.0.1", "100.64.1.5"]
  When I run "iw server status"
  Then I see "Server running on 127.0.0.1:9876, 100.64.1.5:9876"
  And I see the tracking information
  And I see the uptime information
```

**Estimated Effort:** 1-2h
**Complexity:** Straightforward

**Technical Feasibility:**
Straightforward. Requires:
- Store hosts in ServerState or pass to CaskServer
- Update `/api/status` endpoint to include hosts array
- Update status display logic to show all host:port combinations

**Acceptance:**
- `iw server status` displays all bound hosts and port
- Status output lists each interface clearly

---

### Story 3: Warn users about security implications

```gherkin
Feature: Security warning for non-localhost binding
  As a security-conscious developer
  I want to be warned when binding to non-localhost interfaces
  So that I'm aware of the security implications

Scenario: Warning shown when binding to 0.0.0.0
  Given the config file has hosts: ["0.0.0.0"]
  When I run "iw server start"
  Then I see a warning "WARNING: Server is accessible from all network interfaces"
  And I see "Ensure your firewall is properly configured"
  And the server starts successfully

Scenario: Warning shown when binding to non-localhost IP
  Given the config file has hosts: ["127.0.0.1", "100.64.1.5"]
  When I run "iw server start"
  Then I see a warning "WARNING: Server is accessible from non-localhost interfaces: 100.64.1.5"
  And the server starts successfully

Scenario: No warning when binding only to localhost
  Given the config file has hosts: ["localhost"] or no hosts field
  When I run "iw server start"
  Then I do not see any security warnings
  And the server starts successfully
```

**Estimated Effort:** 1h
**Complexity:** Straightforward

**Technical Feasibility:**
Straightforward. Simple validation and warning logic:
- Check if any host is non-localhost
- Print warning listing exposed interfaces
- No changes to server functionality, purely informational

**Acceptance:**
- Warning is displayed when binding to `0.0.0.0`
- Warning is displayed listing any non-localhost IPs
- No warning when only localhost/127.0.0.1 configured
- Warning is clear and actionable

---

## Architectural Sketch

**Purpose:** List WHAT components each story needs, not HOW they're implemented.

### For Story 1: Configure dashboard to bind to multiple interfaces

**Domain Layer:**
- `ServerConfig` - Add `hosts: Seq[String]` field with default `Seq("localhost")`
- Validation logic for host values (localhost variants, 0.0.0.0, valid IPs)

**Application Layer:**
- `ServerConfigRepository` - Handle optional `hosts` field during deserialization (backward compatibility)

**Infrastructure Layer:**
- `ProcessManager.spawnServerProcess` - Accept hosts parameter and pass to daemon
- `CaskServer` - Configure multiple Undertow listeners (one per host)
- `server-daemon.scala` - Parse hosts argument from command line

**Presentation Layer:**
- `server.scala` - Update startup messages to show all bound hosts
- `server.scala` - Pass hosts from config to ProcessManager

---

### For Story 2: Display bound hosts in server status

**Domain Layer:**
- No new domain objects needed

**Application Layer:**
- No new services needed

**Infrastructure Layer:**
- `CaskServer` - Store hosts as instance field
- `/api/status` endpoint - Include hosts array in response JSON

**Presentation Layer:**
- `server.scala` - Update status display to show all "host:port" combinations
- Status formatting logic - Handle multiple hosts appropriately

---

### For Story 3: Warn users about security implications

**Domain Layer:**
- Security validation function - Identify non-localhost hosts in the list

**Application Layer:**
- No new services needed

**Infrastructure Layer:**
- No changes needed

**Presentation Layer:**
- `server.scala` - Add warning logic in `startServer()` function
- Warning message formatting listing exposed interfaces

---

## Total Estimates

**Story Breakdown:**
- Story 1 (Configure multiple host bindings): 4-5 hours
- Story 2 (Display in status): 1-2 hours
- Story 3 (Security warnings): 1 hour

**Total Range:** 6-8 hours

**Confidence:** High

**Reasoning:**
- Well-understood problem domain (network binding configuration)
- Clear scope with minimal architectural changes
- Existing patterns in codebase for config management
- Undertow supports multiple listeners
- Testing is straightforward (start server, check bindings, verify status)

## Testing Approach

**Per Story Testing:**

Each story should have:
1. **Unit Tests**: Config deserialization, validation logic, domain rules
2. **Integration Tests**: Config file I/O, process spawning with hosts parameter
3. **E2E Scenario Tests**: Automated verification of the Gherkin scenario

**Story-Specific Testing Notes:**

**Story 1: Configure multiple host bindings**
- Unit:
  - `ServerConfig` deserialization with/without hosts field
  - Host validation logic (valid/invalid values)
  - Default value handling (missing hosts → `["localhost"]`)
- Integration:
  - `ServerConfigRepository` reads config with hosts field
  - `ProcessManager` spawns server with hosts parameter
  - `CaskServer` binds to all configured hosts
- E2E:
  - Create config with multiple hosts, start server, verify all bindings
  - Start server without hosts field, verify defaults to localhost
  - Start server with invalid host, verify clear error message

**Story 2: Display in status**
- Unit:
  - Status endpoint response includes hosts field
  - Status formatting logic for single and multiple hosts
- Integration:
  - Status endpoint returns correct hosts from config
- E2E:
  - Start server with multiple hosts, run `iw server status`, verify output shows all host:port

**Story 3: Security warnings**
- Unit:
  - Warning detection logic (any non-localhost host?)
  - Warning message formatting with list of exposed interfaces
- E2E:
  - Start server with `"hosts": ["0.0.0.0"]`, verify warning appears
  - Start server with `"hosts": ["127.0.0.1", "100.64.1.5"]`, verify warning lists 100.64.1.5
  - Start server with `"hosts": ["localhost"]`, verify no warning

**Regression Coverage:**
- Verify existing localhost binding still works (default behavior)
- Verify config without hosts field doesn't break
- Verify server start/stop/status commands still work
- Verify health checks still succeed (always use localhost)

## Deployment Considerations

### Database Changes
No database changes needed.

### Configuration Changes

**Story 1:**
- Config file schema extended with optional `hosts` field (array)
- Example: `~/.local/share/iw/server/config.json`
  ```json
  {
    "port": 9876,
    "hosts": ["127.0.0.1", "100.64.1.5"]
  }
  ```
- Backward compatible - missing field defaults to `["localhost"]`

### Rollout Strategy
- Feature is purely configuration-driven
- Users can opt-in by adding `hosts` field to their config
- Default behavior unchanged (localhost binding only)
- No feature flags needed (configuration IS the flag)

### Rollback Plan
- If issues occur, users can remove `hosts` field from config (falls back to localhost)
- No code rollback needed - feature is isolated to config handling

## Dependencies

### Prerequisites
- Existing server infrastructure (IWLE-100) must be in place
- Config file mechanism already exists

### Story Dependencies
- Story 2 depends on Story 1 (needs hosts to be configurable before we can display them)
- Story 3 is independent (can be implemented in parallel with Story 2)

### External Blockers
None identified.

---

## Implementation Sequence

**Recommended Story Order:**

1. **Story 1: Configure multiple host bindings** - Establishes foundation, core functionality
2. **Story 2: Display in status** - Builds on Story 1, adds visibility
3. **Story 3: Security warnings** - Polish, can be done parallel to Story 2 or after

**Iteration Plan:**

- **Iteration 1** (Story 1): Core hosts configuration functionality - 4-5h
- **Iteration 2** (Stories 2-3): Status display and security warnings - 2-3h

Total implementation time: 6-8 hours across 2 iterations

## Documentation Requirements

- [ ] Update README or user guide with hosts configuration examples
- [ ] Document security considerations for non-localhost bindings
- [ ] Update configuration reference with hosts field options
- [ ] Example config snippets for common scenarios

**Configuration Examples to Document:**

```json
// Default: localhost only (no hosts field needed)
{
  "port": 9876
}

// Localhost and Tailscale IP
{
  "port": 9876,
  "hosts": ["127.0.0.1", "100.64.1.5"]
}

// All interfaces (use with caution)
{
  "port": 9876,
  "hosts": ["0.0.0.0"]
}
```

---

**Analysis Status:** Ready for Implementation

**Next Steps:**
1. `/iterative-works:ag-create-tasks IWLE-110` - Generate phase-based task index
2. `/iterative-works:ag-implement IWLE-110` - Begin story-by-story implementation (TDD approach)
