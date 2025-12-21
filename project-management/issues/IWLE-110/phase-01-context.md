# Phase 1: Configure dashboard to bind to multiple interfaces

**Issue:** IWLE-110  
**Phase:** 1 of 3  
**Estimated Effort:** 4-5 hours  
**Status:** Ready for Implementation

## Goals

This phase establishes the foundation for multi-interface binding by:

1. **Extending the configuration model** to support multiple host addresses
2. **Implementing backward compatibility** so existing configs continue working
3. **Configuring Undertow** to bind to all specified interfaces
4. **Passing host configuration** through the complete process spawning chain
5. **Updating startup messages** to show all bound addresses

After this phase, users can configure the dashboard to bind to multiple interfaces (e.g., localhost + Tailscale IP) without exposing it on all network interfaces.

## Scope

### In Scope

- Add `hosts: Seq[String]` field to `ServerConfig` with default `Seq("localhost")`
- Validate host values (localhost variants, 0.0.0.0, valid IPs)
- Handle missing `hosts` field during deserialization (backward compatibility)
- Pass hosts parameter through `ProcessManager.spawnServerProcess`
- Parse hosts from command line arguments in `server-daemon.scala`
- Configure Undertow to create multiple listeners (one per host)
- Update startup success message to display all "host:port" combinations
- Health check continues using `localhost` regardless of binding configuration

### Out of Scope

- Displaying hosts in `iw server status` command (Phase 2)
- Security warnings for non-localhost bindings (Phase 3)
- Advanced network validation (DNS resolution, interface existence checks)
- IPv6 bracket notation handling (future enhancement if needed)

## Dependencies

### Prerequisites

This is Phase 1, so no dependency on previous phases.

**External prerequisites:**
- Server infrastructure from IWLE-100 must be in place
- Undertow HTTP server library supports multiple listeners
- Config file mechanism (`ServerConfigRepository`) exists

### Blocked By

None - this is the foundational phase.

### Blocks

- **Phase 2** (Display in status) - needs hosts configuration to exist
- **Phase 3** (Security warnings) - needs hosts configuration to validate

## Technical Approach

### High-Level Strategy

We'll extend the existing configuration system with a new `hosts` field while maintaining full backward compatibility:

1. **Domain Model Extension**: Add `hosts: Seq[String]` to `ServerConfig` case class with a sensible default
2. **Validation Layer**: Implement host validation to catch invalid values early
3. **Backward Compatibility**: Use upickle's default value handling to default missing `hosts` field to `Seq("localhost")`
4. **Process Chain Update**: Thread hosts parameter through the entire spawn chain
5. **Undertow Configuration**: Replace single `.addHttpListener()` with multiple listeners (one per host)
6. **Messaging Update**: Change startup output to show all bound addresses

### Why This Approach

**Multiple listeners vs bind to 0.0.0.0:**
- Security - only bind to intended interfaces
- Explicit configuration - no surprises about what's exposed
- Firewall misconfiguration protection

**Array field vs single host:**
- Supports localhost + Tailscale simultaneously
- No need to choose between local access and remote access
- User explicitly lists what should be accessible

**Default to localhost:**
- Safe default - doesn't expose service on network
- Backward compatible - existing behavior preserved
- Explicit opt-in for network exposure

**Health check always uses localhost:**
- Reliable - localhost always available when server running locally
- Simple - no need to track "primary" host
- Independent - health check works regardless of binding config

## Files to Modify/Create

### Domain Layer

**File:** `/home/mph/Devel/projects/iw-cli-IWLE-110/.iw/core/ServerConfig.scala`

**Changes:**
- Add `hosts: Seq[String] = Seq("localhost")` field to case class
- Add `validateHost(host: String): Either[String, String]` function
- Add `validateHosts(hosts: Seq[String]): Either[String, Seq[String]]` function
- Update `create()` method to validate hosts in addition to port

**Validation rules:**
- Reject empty hosts array
- Each host must be one of:
  - `localhost`, `127.0.0.1`, `::1` (localhost variants)
  - `0.0.0.0`, `::` (bind all interfaces)
  - Valid IPv4 address (basic pattern check)
  - Valid IPv6 address (basic pattern check)

### Application Layer

**File:** `/home/mph/Devel/projects/iw-cli-IWLE-110/.iw/core/ServerConfigRepository.scala`

**Changes:**
- No code changes needed - upickle will automatically handle default value for missing `hosts` field
- Verify backward compatibility in tests

### Infrastructure Layer

**File:** `/home/mph/Devel/projects/iw-cli-IWLE-110/.iw/core/ProcessManager.scala`

**Changes:**
- Update `spawnServerProcess(statePath: String, port: Int)` signature to accept hosts parameter
- Pass hosts as comma-separated string in command line args

**File:** `/home/mph/Devel/projects/iw-cli-IWLE-110/.iw/core/CaskServer.scala`

**Changes:**
- Update `CaskServer` class constructor to accept `hosts: Seq[String]`
- Update `start()` method signature to accept hosts parameter
- Replace single `.addHttpListener()` call with loop over hosts

**File:** `/home/mph/Devel/projects/iw-cli-IWLE-110/.iw/commands/server-daemon.scala`

**Changes:**
- Update argument parsing to expect 3 args: `<statePath> <port> <hosts>`
- Parse hosts from comma-separated string
- Pass hosts to `CaskServer.start()`

### Presentation Layer

**File:** `/home/mph/Devel/projects/iw-cli-IWLE-110/.iw/commands/server.scala`

**Changes in `startServer()` function:**
- Pass `config.hosts` to `ProcessManager.spawnServerProcess()`
- Update success message to display all "host:port" combinations
- Health check remains unchanged (always uses `localhost`)

## Testing Strategy

### Unit Tests

**File:** `/home/mph/Devel/projects/iw-cli-IWLE-110/.iw/core/test/ServerConfigTest.scala`

**New tests:**
- Verify `hosts` field exists and defaults correctly
- Validate host acceptance of localhost variants
- Validate host acceptance of bind-all addresses
- Validate host acceptance of valid IPv4
- Validate host rejection of invalid values
- Validate rejection of empty hosts array
- Verify `create()` validates hosts

**File:** `/home/mph/Devel/projects/iw-cli-IWLE-110/.iw/core/test/ServerConfigRepositoryTest.scala`

**New tests:**
- Deserialize config without hosts field (backward compatibility)
- Deserialize config with hosts field (explicit values preserved)
- Deserialize config with empty hosts array (validation failure)

### Integration Tests

**File:** `/home/mph/Devel/projects/iw-cli-IWLE-110/.iw/core/test/ProcessManagerTest.scala`

**New tests:**
- Verify `spawnServerProcess` accepts hosts parameter
- Verify hosts passed to daemon process

**File:** `/home/mph/Devel/projects/iw-cli-IWLE-110/.iw/core/test/CaskServerTest.scala`

**New tests:**
- Verify `CaskServer` accepts hosts parameter
- Verify `start()` creates listener for each host

### E2E Tests

E2E tests will verify the complete flow from config file to server binding.

**Key scenarios:**
1. Server binds to multiple configured hosts
2. Server defaults to localhost when hosts not in config
3. Server rejects invalid host in config

## Acceptance Criteria

Phase 1 is complete when:

- **AC1: Config model extended**
  - `ServerConfig` has `hosts: Seq[String]` field
  - Default value is `Seq("localhost")`
  - Validation rejects empty array and invalid host values

- **AC2: Backward compatibility maintained**
  - Config files without `hosts` field deserialize successfully
  - Default behavior (localhost-only binding) is preserved
  - Existing config files continue working without modification

- **AC3: Hosts passed through spawn chain**
  - `ProcessManager.spawnServerProcess` accepts hosts parameter
  - `server-daemon.scala` parses hosts from command line
  - `CaskServer.start` receives and uses hosts parameter

- **AC4: Undertow binds to all hosts**
  - Server creates one listener per host in the array
  - Each configured interface accepts connections
  - Health check endpoint accessible on all bound interfaces

- **AC5: Startup messages updated**
  - Success message displays all "host:port" combinations
  - Format: "Server started on 127.0.0.1:9876, 100.64.1.5:9876"
  - Health check continues using localhost

- **AC6: Validation works**
  - Invalid host values rejected with clear error
  - Empty hosts array rejected
  - Valid hosts (localhost, IPs, 0.0.0.0) accepted

- **AC7: Tests pass**
  - All unit tests pass
  - All integration tests pass
  - E2E scenarios pass

## Implementation Checklist

### 1. Domain Model (ServerConfig.scala)

- Add `hosts: Seq[String] = Seq("localhost")` field to case class
- Implement `validateHost(host: String): Either[String, String]`
- Implement `validateHosts(hosts: Seq[String]): Either[String, Seq[String]]`
- Update `create()` method to validate hosts
- Write unit tests for validation logic

### 2. Infrastructure (Process Spawning Chain)

- Update `ProcessManager.spawnServerProcess` signature to accept hosts
- Pass hosts as comma-separated arg in ProcessBuilder
- Update `server-daemon.scala` to parse hosts from args
- Update `CaskServer` class to accept hosts in constructor
- Update `CaskServer.start()` to accept hosts parameter
- Configure Undertow with multiple listeners
- Write integration tests for updated signatures

### 3. Presentation (server.scala)

- Pass `config.hosts` to `ProcessManager.spawnServerProcess`
- Update success message to show all host:port combinations
- Ensure health check still uses localhost
- Manual verification: start server with multiple hosts config

### 4. Testing

- Write unit tests for ServerConfig validation
- Write unit tests for backward compatibility
- Update integration tests for signature changes
- Create E2E tests for scenarios
- Run full test suite

### 5. Verification

- Create config with multiple hosts
- Start server and verify success message shows both addresses
- Verify server accepts connections on both interfaces
- Create config without `hosts` field and verify defaults to localhost
- Create config with invalid host and verify clear error message

## Risk Mitigation

### Risk: Breaking existing configurations

**Likelihood:** Low  
**Impact:** High

**Mitigation:**
- Use upickle default value for missing `hosts` field
- Extensive backward compatibility testing
- Default behavior identical to current (localhost-only)

### Risk: Undertow binding failures on some interfaces

**Likelihood:** Medium  
**Impact:** Medium

**Mitigation:**
- Validate hosts before spawning process
- Clear error messages if binding fails
- Fail fast approach - all-or-nothing to prevent confusing partial states

### Risk: Security - accidentally exposing on all interfaces

**Likelihood:** Low (with validation)  
**Impact:** High

**Mitigation:**
- Validation catches 0.0.0.0 (warning in Phase 3)
- Default is localhost-only (safe)
- Documentation will emphasize security implications

## Open Questions

All questions resolved during analysis:

- Single `host` vs `hosts` array? → **DECIDED:** Array for multiple simultaneous bindings
- Default value when missing? → **DECIDED:** `["localhost"]` for backward compatibility
- Health check behavior? → **DECIDED:** Always use localhost regardless of binding
- Validation strictness? → **DECIDED:** Basic pattern validation, clear error messages

## Notes for Implementation

### Development Workflow (TDD)

Follow strict TDD for this phase:

1. **Red**: Write failing unit test for validation
2. **Green**: Implement validation to pass test
3. **Refactor**: Clean up if needed
4. **Red**: Write failing integration test for process spawning
5. **Green**: Update ProcessManager to pass hosts
6. **Continue** through all components

### Testing Local vs. Real Network Interfaces

For E2E tests, use:
- `127.0.0.1` + `0.0.0.0` (always available)
- Don't assume Tailscale IPs in automated tests
- Document manual testing steps for Tailscale in acceptance criteria

### Commit Strategy

Suggested commits:
1. "Add hosts field to ServerConfig with validation"
2. "Thread hosts through process spawning chain"
3. "Configure Undertow for multiple listeners"
4. "Update startup messages to show all hosts"
5. "Add E2E tests for multi-host binding"

Each commit should include relevant tests and keep the build passing.

---

**Ready for Implementation:** Yes  
**Next Step:** Begin TDD implementation starting with ServerConfig domain model
