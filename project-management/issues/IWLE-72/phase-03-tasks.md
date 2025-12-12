# Phase 3 Tasks: Validate environment and configuration

**Issue:** IWLE-72
**Phase:** 3 of 7
**Status:** Ready for Implementation

---

## Setup

- [x] [impl] Review existing code structure from Phase 1 & 2

## Core Domain (DoctorChecks)

- [x] [test] Write tests for CheckResult enum formatting
- [x] [impl] Create CheckResult enum in `.iw/core/DoctorChecks.scala`
- [x] [test] Write tests for DoctorChecks registry (register, runAll)
- [x] [impl] Implement DoctorChecks registry object

## Process Adapter

- [x] [test] Write tests for ProcessAdapter.commandExists
- [x] [impl] Create ProcessAdapter in `.iw/core/Process.scala`

## Linear API Client

- [x] [test] Write integration tests for LinearClient.validateToken
- [x] [impl] Create LinearClient in `.iw/core/LinearClient.scala`

## Bootstrap Script Update

- [ ] [test] Write BATS tests for hook file discovery pattern
- [x] [impl] Update `iw` bootstrap script with hook discovery

## Doctor Command

- [x] [test] Write tests for doctor command base checks (git repo, config)
- [x] [impl] Implement doctor command in `.iw/commands/doctor.scala`
- [x] [impl] Add base checks (git repository, configuration file)

## Hook Files

- [x] [test] Write tests for issue hook doctor check (Linear token)
- [x] [impl] Create `issue.hook-doctor.scala` with Linear token check
- [x] [test] Write tests for start hook doctor check (tmux)
- [x] [impl] Create `start.hook-doctor.scala` with tmux check

## Integration & E2E

- [x] [test] Write BATS E2E tests for complete doctor workflow
- [x] [impl] Verify all checks pass with valid environment
- [x] [impl] Verify proper exit codes and error messages

---

## Notes

- Hook files use `*.hook-{command}.scala` naming convention
- Pure check functions enable unit testing without side effects
- Bootstrap script uses `find` for safe glob handling
