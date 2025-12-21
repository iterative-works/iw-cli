# Phase 1 Implementation Tasks: Configure dashboard to bind to multiple interfaces

**Issue:** IWLE-110
**Phase:** 1 of 3
**Status:** Ready for Implementation

## Task Groups

### Setup Tasks

- [x] [setup] Verify all prerequisite files exist (ServerConfig.scala, ProcessManager.scala, CaskServer.scala, server-daemon.scala, server.scala)

### Domain Model Tasks (ServerConfig)

- [x] [test] Write test for hosts field exists with default value Seq("localhost")
- [x] [impl] Add hosts field to ServerConfig case class with default Seq("localhost")

- [x] [test] Write test for validateHost accepts "localhost"
- [x] [test] Write test for validateHost accepts "127.0.0.1"
- [x] [test] Write test for validateHost accepts "::1"
- [x] [test] Write test for validateHost accepts "0.0.0.0"
- [x] [test] Write test for validateHost accepts "::"
- [x] [test] Write test for validateHost accepts valid IPv4 "192.168.1.1"
- [x] [test] Write test for validateHost accepts valid IPv6 "2001:db8::1"
- [x] [test] Write test for validateHost rejects invalid value "not-a-host"
- [x] [test] Write test for validateHost rejects empty string
- [x] [impl] Implement validateHost function with basic validation

- [x] [test] Write test for validateHosts accepts non-empty array of valid hosts
- [x] [test] Write test for validateHosts rejects empty array
- [x] [test] Write test for validateHosts rejects array containing invalid host
- [x] [impl] Implement validateHosts function

- [x] [test] Write test for create() validates hosts and returns error for invalid hosts
- [x] [test] Write test for create() validates hosts and returns error for empty array
- [x] [test] Write test for create() accepts valid port and valid hosts
- [x] [impl] Update create() method to validate both port and hosts

### Repository Tasks (Backward Compatibility)

- [x] [test] Write test for deserializing config without hosts field defaults to Seq("localhost")
- [x] [test] Write test for deserializing config with hosts field preserves explicit values
- [x] [test] Write test for deserializing config with empty hosts array fails validation
- [x] [test] Write test for serializing config with hosts field includes hosts in JSON
- [x] [impl] Verify upickle handles default value correctly (no code changes needed, tests verify behavior)

### Infrastructure Tasks (Process Spawning)

- [ ] [test] Write test for ProcessManager.spawnServerProcess signature accepts hosts parameter
- [ ] [test] Write test for ProcessManager.spawnServerProcess passes hosts as comma-separated string to daemon
- [x] [impl] Update ProcessManager.spawnServerProcess signature to accept hosts: Seq[String]
- [x] [impl] Update ProcessManager.spawnServerProcess to pass hosts as comma-separated arg

- [ ] [test] Write test for server-daemon.scala parses three arguments including hosts
- [ ] [test] Write test for server-daemon.scala splits comma-separated hosts into Seq
- [x] [impl] Update server-daemon.scala argument parsing to expect 3 args: statePath, port, hosts
- [x] [impl] Update server-daemon.scala to parse comma-separated hosts string

- [ ] [test] Write test for CaskServer constructor accepts hosts parameter
- [ ] [test] Write test for CaskServer.start accepts hosts parameter
- [ ] [test] Write test for CaskServer.start creates listener for each host in array
- [ ] [impl] Update CaskServer class constructor to accept hosts: Seq[String]
- [x] [impl] Update CaskServer.start method to accept hosts parameter
- [x] [impl] Replace single addHttpListener with loop over hosts creating multiple listeners

### Presentation Layer Tasks (Server Command)

- [ ] [test] Write test for server.scala startServer passes config.hosts to ProcessManager
- [x] [impl] Update server.scala startServer to pass config.hosts to ProcessManager.spawnServerProcess

- [ ] [test] Write test for success message displays all host:port combinations
- [ ] [test] Write test for success message with single host shows "Server started on localhost:9876"
- [ ] [test] Write test for success message with multiple hosts shows "Server started on 127.0.0.1:9876, 100.64.1.5:9876"
- [x] [impl] Update server.scala success message to display all host:port combinations

- [ ] [test] Write test verifying health check always uses localhost regardless of binding config
- [x] [impl] Verify health check implementation (no changes needed, confirm it uses localhost)

### Integration Testing

- [ ] [integration] Write integration test for complete flow: config with multiple hosts -> server starts -> all interfaces accept connections
- [ ] [integration] Write integration test for backward compatibility: config without hosts -> server starts -> defaults to localhost
- [ ] [integration] Write integration test for validation: config with invalid host -> clear error message -> server does not start

### End-to-End Testing

- [ ] [integration] Write E2E test scenario: Server binds to multiple configured hosts
- [ ] [integration] Write E2E test scenario: Server defaults to localhost when hosts not in config
- [ ] [integration] Write E2E test scenario: Server rejects invalid host in config

### Verification Tasks

- [x] [integration] Run all unit tests and verify they pass
- [ ] [integration] Run all integration tests and verify they pass
- [ ] [integration] Run all E2E tests and verify they pass
- [ ] [integration] Manual test: Create config with multiple hosts, start server, verify success message
- [ ] [integration] Manual test: Verify server accepts connections on all configured interfaces
- [ ] [integration] Manual test: Create config without hosts field, verify defaults to localhost
- [ ] [integration] Manual test: Create config with invalid host, verify clear error message

## Notes

### TDD Workflow Reminder

For EVERY implementation task:
1. **Red**: Write failing test first
2. **Green**: Implement minimum code to pass
3. **Refactor**: Clean up while keeping tests green
4. **Repeat**: Add additional test cases

### Validation Rules Reference

Valid host values:
- `localhost`, `127.0.0.1`, `::1` (localhost variants)
- `0.0.0.0`, `::` (bind all interfaces)
- Valid IPv4 addresses (basic pattern: `\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}`)
- Valid IPv6 addresses (basic pattern: contains `:` and valid hex digits)

Invalid values:
- Empty string
- Arbitrary text not matching patterns
- Empty array (must have at least one host)

### Success Message Format

- Single host: `"Server started on localhost:9876"`
- Multiple hosts: `"Server started on 127.0.0.1:9876, 100.64.1.5:9876"`

### Testing Notes

- Tests should use localhost variants (127.0.0.1, ::1) and 0.0.0.0 for automated testing
- Don't assume Tailscale IPs in automated tests
- Document manual testing steps for real Tailscale IPs in verification tasks

### Commit Strategy

Suggested commits (each with passing tests):
1. "Add hosts field and validation to ServerConfig domain model"
2. "Add backward compatibility tests for ServerConfig deserialization"
3. "Thread hosts parameter through process spawning chain"
4. "Configure Undertow for multiple listeners per host"
5. "Update startup messages to show all bound addresses"
6. "Add integration and E2E tests for multi-host binding"

Each commit should:
- Include relevant tests
- Keep the build passing
- Focus on single cohesive change
