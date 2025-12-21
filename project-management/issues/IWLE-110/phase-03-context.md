# Phase 3 Context: Warn users about security implications

**Issue:** IWLE-110
**Phase:** 3 of 3
**Status:** Ready for Implementation

## Goals

Implement security warnings when users configure the dashboard server to bind to non-localhost interfaces. This helps users understand the security implications of exposing the server to external networks.

## Scope

### In Scope
- Security warning when binding to `0.0.0.0` or `::` (all interfaces)
- Security warning listing specific non-localhost IPs when configured
- No warning when only localhost variants are configured (localhost, 127.0.0.1, ::1)
- Warning shown at server startup (before server starts successfully)

### Out of Scope
- Blocking non-localhost bindings (warnings only, user can proceed)
- Network security features (firewall integration, etc.)
- Runtime warnings (only at startup)
- Documentation updates (separate task)

## Dependencies

### From Previous Phases
- **Phase 1:** `ServerConfig.hosts` field exists with validation
- **Phase 1:** Host validation utilities (`validateHost()`, `validateHosts()`)
- **Phase 2:** Status display shows bound hosts

### Available Utilities
- `ServerConfig.validateHost(host: String)` - Can use to identify host type
- `ServerLifecycleService.formatHostsDisplay(hosts, port)` - For consistent formatting

## Technical Approach

### Domain Layer
Add security analysis function to identify which hosts expose the server:
- Pure function: `analyzeHostsSecurity(hosts: Seq[String]): SecurityAnalysis`
- Returns: whether any non-localhost hosts exist, list of exposed hosts, whether binding to all interfaces

### Presentation Layer
Add warning output in `startServer()` before the success message:
- Check if configured hosts include non-localhost interfaces
- Print appropriate warning with list of exposed interfaces
- Proceed with normal startup (warning is informational only)

### No Changes Needed
- Infrastructure layer (server binding unchanged)
- Application layer (no new services needed)

## Files to Modify

1. **`.iw/core/ServerConfig.scala`** or **`.iw/core/ServerLifecycleService.scala`**
   - Add security analysis function
   - Pure function to categorize hosts as localhost vs non-localhost

2. **`.iw/commands/server.scala`**
   - Add warning display logic in `startServer()`
   - Call security analysis before/after starting server
   - Display formatted warning with exposed interfaces

3. **`.iw/core/test/ServerConfigTest.scala`** or **`.iw/core/test/ServerLifecycleServiceTest.scala`**
   - Unit tests for security analysis function

## Testing Strategy

### Unit Tests
- `isLocalhostVariant("localhost")` returns true
- `isLocalhostVariant("127.0.0.1")` returns true
- `isLocalhostVariant("::1")` returns true
- `isLocalhostVariant("0.0.0.0")` returns false
- `isLocalhostVariant("100.64.1.5")` returns false
- `analyzeHostsSecurity(["localhost"])` returns no warnings
- `analyzeHostsSecurity(["0.0.0.0"])` returns bind-all warning
- `analyzeHostsSecurity(["127.0.0.1", "100.64.1.5"])` returns exposed hosts warning

### E2E Tests
- Start server with `["0.0.0.0"]` → verify warning in output
- Start server with `["127.0.0.1", "100.64.1.5"]` → verify warning lists the IP
- Start server with `["localhost"]` → verify no warning

## Acceptance Criteria

From Story 3 in analysis.md:

1. **Warning for 0.0.0.0**: When config has `hosts: ["0.0.0.0"]`, warning says "WARNING: Server is accessible from all network interfaces"
2. **Warning for non-localhost IPs**: When config has `hosts: ["127.0.0.1", "100.64.1.5"]`, warning says "WARNING: Server is accessible from non-localhost interfaces: 100.64.1.5"
3. **No warning for localhost-only**: When config has `hosts: ["localhost"]` or no hosts field, no security warning is displayed
4. **Warning is clear and actionable**: Include guidance like "Ensure your firewall is properly configured"
5. **Server starts successfully**: Warning does not prevent server from starting

## Design Decisions

### Warning Placement
Display warning BEFORE the success message so it's visible but doesn't hide startup info.

### Warning Format
```
⚠️  WARNING: Server is accessible from all network interfaces (0.0.0.0)
   Ensure your firewall is properly configured.
```
or
```
⚠️  WARNING: Server is accessible from non-localhost interfaces: 100.64.1.5, 192.168.1.100
   Ensure your firewall is properly configured.
```

### Localhost Variants
These are considered "safe" (no warning): `localhost`, `127.0.0.1`, `::1`, `127.x.x.x`

### Non-Localhost Detection
- `0.0.0.0` and `::` are special (bind-all, extra warning)
- Any other IP address is "exposed"

## Estimated Effort

1 hour total:
- 30 min: Security analysis function + unit tests
- 15 min: Warning display logic in server.scala
- 15 min: E2E verification and polish
