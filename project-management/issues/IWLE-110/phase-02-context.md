# Phase 2 Context: Display bound hosts in server status

**Issue:** IWLE-110
**Phase:** 2 of 3
**Status:** Ready for Implementation

## Goals

Display all bound host interfaces when showing server status, so users know how to access the dashboard from different network locations.

## Scope

### In Scope
- Update `/api/status` endpoint to include hosts array in response JSON
- Update `iw server status` display to show all "host:port" combinations
- Handle formatting for single and multiple hosts appropriately

### Out of Scope
- Changing how hosts are configured (Phase 1, completed)
- Security warnings for non-localhost bindings (Phase 3)
- Any modifications to server startup logic

## Dependencies from Previous Phases

**Phase 1 provided:**
- `ServerConfig.hosts: Seq[String]` field with default `Seq("localhost")`
- `CaskServer.start()` now accepts hosts parameter and binds to all configured addresses
- Hosts are passed through the process spawning chain to the daemon
- The daemon receives hosts as command-line argument `--hosts`

**What Phase 2 builds on:**
- The server daemon has access to the hosts configuration
- CaskServer is instantiated with the hosts parameter

## Technical Approach

### Current Status Flow

1. User runs `iw server status`
2. `server.scala` calls `ServerClient.status()`
3. `ServerClient` fetches from `http://localhost:{port}/api/status`
4. Response is parsed and displayed

### Changes Required

1. **CaskServer** - Store hosts as instance field and expose in `/api/status` endpoint
2. **ServerClient** - Parse hosts from status response JSON
3. **server.scala** - Format status display to show all host:port combinations

### Data Flow

```
CaskServer (has hosts) → /api/status JSON → ServerClient → server.scala display
```

## Files to Modify

| File | Change |
|------|--------|
| `.iw/core/CaskServer.scala` | Store hosts, include in status endpoint response |
| `.iw/core/ServerClient.scala` | Parse hosts from status JSON, update return type if needed |
| `.iw/commands/server.scala` | Update status display formatting |
| `.iw/core/test/ServerClientTest.scala` | Test hosts parsing (if exists) or create |
| `.iw/core/test/CaskServerTest.scala` | Test status endpoint includes hosts (if feasible) |

## Testing Strategy

### Unit Tests
- CaskServer status endpoint JSON includes hosts field
- ServerClient correctly parses hosts from status response
- Status formatting logic handles single and multiple hosts

### Integration Tests
- Full status flow: start server with hosts, call status, verify display

### Manual Verification
- Start server with multiple hosts config
- Run `iw server status`
- Verify output shows all host:port combinations

## Acceptance Criteria

From analysis.md Story 2:

```gherkin
Scenario: Status shows all configured hosts
  Given the server is running with hosts ["127.0.0.1", "100.64.1.5"]
  When I run "iw server status"
  Then I see "Server running on 127.0.0.1:9876, 100.64.1.5:9876"
  And I see the tracking information
  And I see the uptime information
```

**Specific acceptance:**
- `iw server status` displays all bound hosts and port
- Status output lists each interface clearly
- Single host case: `Server running on localhost:9876`
- Multiple hosts case: `Server running on 127.0.0.1:9876, 100.64.1.5:9876`

## Implementation Notes

- The status endpoint currently returns tracking info and uptime - we're adding hosts
- ServerClient already parses JSON from status endpoint - extend parsing
- Keep backward compatibility if server is older and doesn't return hosts field
- Match existing formatting patterns in server.scala status display

## Estimated Effort

1-2 hours (as per analysis.md)

---

**Ready for task generation and implementation.**
