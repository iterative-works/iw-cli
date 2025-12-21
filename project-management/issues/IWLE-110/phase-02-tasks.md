# Phase 2 Tasks: Display bound hosts in server status

**Issue:** IWLE-110
**Phase:** 2 of 3
**Status:** 0/8 tasks complete

## Setup

- [ ] [setup] Read and understand current status flow in server.scala, ServerClient, CaskServer

## Tests

- [ ] [test] Write test for CaskServer status endpoint including hosts in response JSON
- [ ] [test] Write test for status display formatting with single host
- [ ] [test] Write test for status display formatting with multiple hosts

## Implementation

- [ ] [impl] Update CaskServer to store hosts as instance field and include in /api/status response
- [ ] [impl] Update server.scala showStatus() to read hosts from status JSON and display all host:port combinations

## Integration

- [ ] [integration] Verify status endpoint returns hosts field with running server
- [ ] [integration] Verify `iw server status` displays all bound host:port combinations

## Notes

- CaskServer constructor needs to receive hosts parameter (currently only passed to start())
- Status endpoint currently returns: status, port, worktreeCount, startedAt
- Add hosts array to response
- Display format: "Server running on host1:port, host2:port"
- Backward compatibility: if hosts field missing in response, fall back to showing just port
