# Phase 2 Tasks: Display bound hosts in server status

**Issue:** IWLE-110
**Phase:** 2 of 3
**Phase Status:** Complete

## Setup

- [x] [setup] Read and understand current status flow in server.scala, ServerClient, CaskServer

## Tests

- [x] [test] Write test for CaskServer status endpoint including hosts in response JSON
- [x] [test] Write test for status display formatting with single host
- [x] [test] Write test for status display formatting with multiple hosts

## Implementation

- [x] [impl] Update CaskServer to store hosts as instance field and include in /api/status response
- [x] [impl] Update server.scala showStatus() to read hosts from status JSON and display all host:port combinations

## Integration

- [x] [integration] Verify status endpoint returns hosts field with running server
- [x] [integration] Verify `iw server status` displays all bound host:port combinations

## Notes

- CaskServer constructor needs to receive hosts parameter (currently only passed to start())
- Status endpoint currently returns: status, port, worktreeCount, startedAt
- Add hosts array to response
- Display format: "Server running on host1:port, host2:port"
- Backward compatibility: if hosts field missing in response, fall back to showing just port
