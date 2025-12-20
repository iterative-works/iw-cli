---
generated_from: cef64e259ab76118ce9f4ac5051bbee8dd30455e
generated_at: 2025-12-20T14:30:00Z
branch: IWLE-100-phase-02
issue_id: IWLE-100
phase: 2
files_analyzed:
  - .iw/core/WorktreeRegistrationService.scala
  - .iw/core/ServerClient.scala
  - .iw/core/CaskServer.scala
  - .iw/commands/start.scala
  - .iw/commands/open.scala
  - .iw/commands/issue.scala
  - .iw/core/test/WorktreeRegistrationServiceTest.scala
  - .iw/core/test/CaskServerTest.scala
---

# Review Packet: Phase 2 - Automatic Worktree Registration

**Issue:** IWLE-100
**Phase:** 2 of 7
**Branch:** `IWLE-100-phase-02`

---

## Goals

This phase makes the dashboard feature usable in normal workflow by implementing automatic worktree registration. The objectives are:

1. **Server registration endpoint**: Add `PUT /api/worktrees/{issueId}` to register/update worktrees
2. **HTTP client for CLI commands**: Create `ServerClient` for CLI-to-server communication
3. **Auto-registration in `iw start`**: Register new worktrees after successful creation
4. **Auto-registration in `iw open`**: Register/update existing worktrees when opened
5. **Update timestamp in `iw issue`**: Refresh `lastSeenAt` timestamp for current worktree
6. **Best-effort registration**: Failures must not break CLI command functionality
7. **Lazy server start**: If server isn't running, trigger auto-start before registration

After this phase, developers will have worktrees automatically appear on the dashboard without manual API calls.

---

## Scenarios

- [ ] Creating a worktree with `iw start` registers it automatically with the dashboard
- [ ] Opening a worktree with `iw open` updates its lastSeenAt timestamp
- [ ] Running `iw issue` updates the current worktree's lastSeenAt timestamp
- [ ] Registration failure shows warning but does not break the CLI command
- [ ] Server auto-starts if not running when registration is attempted
- [ ] PUT endpoint creates new registration if worktree doesn't exist
- [ ] PUT endpoint updates existing registration (upsert semantics)
- [ ] PUT endpoint preserves `registeredAt` timestamp on updates

---

## Entry Points

| File | Method/Class | Why Start Here |
|------|--------------|----------------|
| `.iw/core/WorktreeRegistrationService.scala` | `WorktreeRegistrationService.register` | Pure business logic - no I/O, easy to understand domain rules |
| `.iw/core/CaskServer.scala` | `registerWorktree` (PUT endpoint) | HTTP entry point - orchestrates registration flow |
| `.iw/core/ServerClient.scala` | `ServerClient.registerWorktree` | CLI-to-server communication - shows lazy start pattern |
| `.iw/commands/start.scala` | `createWorktreeForIssue` | Primary integration point - see registration in context |
| `.iw/core/test/WorktreeRegistrationServiceTest.scala` | Test suite | Verify expected behavior through test cases |

---

## Diagrams

### Architecture Overview

```mermaid
C4Context
    title Phase 2: CLI-to-Server Registration Flow

    Person(developer, "Developer", "Uses iw CLI commands")

    System_Boundary(cli, "CLI Commands") {
        Container(start, "iw start", "scala-cli", "Creates worktree")
        Container(open, "iw open", "scala-cli", "Opens worktree")
        Container(issue, "iw issue", "scala-cli", "Shows issue info")
    }

    System_Boundary(server, "Dashboard Server") {
        Container(caskServer, "CaskServer", "Cask/Undertow", "HTTP server on port 9876")
        Container(stateRepo, "StateRepository", "Scala", "JSON persistence")
    }

    ContainerDb(stateFile, "state.json", "JSON", "Worktree registrations")

    Rel(developer, start, "runs")
    Rel(developer, open, "runs")
    Rel(developer, issue, "runs")

    Rel(start, caskServer, "PUT /api/worktrees/{id}", "HTTP")
    Rel(open, caskServer, "PUT /api/worktrees/{id}", "HTTP")
    Rel(issue, caskServer, "PUT /api/worktrees/{id}", "HTTP")

    Rel(caskServer, stateRepo, "load/save")
    Rel(stateRepo, stateFile, "read/write")
```

### Component Relationships

```mermaid
graph TB
    subgraph "CLI Layer"
        START[start.scala]
        OPEN[open.scala]
        ISSUE[issue.scala]
    end

    subgraph "Infrastructure Layer"
        SC[ServerClient]
        CS[CaskServer]
        SR[StateRepository]
    end

    subgraph "Service Layer"
        WRS[WorktreeRegistrationService]
        SSS[ServerStateService]
    end

    subgraph "Domain Layer"
        SS[ServerState]
        WR[WorktreeRegistration]
    end

    START --> SC
    OPEN --> SC
    ISSUE --> SC

    SC -->|HTTP PUT| CS
    SC -->|lazy start| CS

    CS --> WRS
    CS --> SSS
    CS --> SR

    WRS --> SS
    WRS --> WR
    SSS --> SS
    SSS --> SR

    SR -->|JSON| STATE[(state.json)]
```

### Registration Flow Sequence

```mermaid
sequenceDiagram
    participant CLI as iw start/open/issue
    participant SC as ServerClient
    participant CS as CaskServer
    participant WRS as WorktreeRegistrationService
    participant SSS as ServerStateService
    participant SR as StateRepository
    participant FS as state.json

    CLI->>SC: registerWorktree(issueId, path, ...)
    SC->>SC: isHealthy(port)?

    alt Server not running
        SC->>CS: start in daemon thread
        SC->>SC: waitForServer(5s)
    end

    SC->>CS: PUT /api/worktrees/{issueId}
    CS->>SR: load state
    SR->>FS: read JSON
    FS-->>SR: state data
    SR-->>CS: ServerState

    CS->>WRS: register(issueId, path, state)
    WRS->>WRS: validate inputs
    WRS->>WRS: create/update registration
    WRS-->>CS: Right(newState)

    CS->>SSS: save(newState)
    SSS->>SR: write state
    SR->>FS: atomic write (tmp + rename)
    FS-->>SR: success
    SR-->>SSS: Right(())
    SSS-->>CS: Right(())

    CS-->>SC: 200 OK + JSON
    SC-->>CLI: Right(())

    Note over CLI: Continue with command execution
```

### Layer Architecture (FCIS)

```mermaid
graph TB
    subgraph "Imperative Shell"
        CLI[CLI Commands]
        HTTP[HTTP Server]
        FS[File System]
    end

    subgraph "Functional Core"
        SVC[WorktreeRegistrationService]
        DOM[Domain Models]
    end

    CLI -->|calls| SC[ServerClient]
    SC -->|HTTP| HTTP
    HTTP -->|delegates| SVC
    SVC -->|pure transforms| DOM
    HTTP -->|persists| FS

    style SVC fill:#90EE90
    style DOM fill:#90EE90
    style CLI fill:#FFB6C1
    style HTTP fill:#FFB6C1
    style FS fill:#FFB6C1
```

---

## Test Summary

| Test | Type | Verifies |
|------|------|----------|
| `register creates new WorktreeRegistration with current timestamp` | Unit | New registration gets both timestamps set to now |
| `register adds WorktreeRegistration to ServerState.worktrees map` | Unit | Registration is stored in state |
| `register returns Right with new ServerState on success` | Unit | Success returns new immutable state |
| `register updates existing worktree's lastSeenAt timestamp` | Unit | Update refreshes lastSeenAt |
| `register preserves registeredAt timestamp on update` | Unit | Original registration time preserved |
| `register updates path/trackerType/team if changed` | Unit | Upsert updates all mutable fields |
| `register returns Left for invalid issue ID format` | Unit | Validation rejects empty issueId |
| `register returns Left for empty path` | Unit | Validation rejects empty path |
| `register returns Left for invalid tracker type` | Unit | Validation rejects empty trackerType |
| `updateLastSeen updates timestamp for existing worktree` | Unit | Timestamp-only update works |
| `updateLastSeen returns Left for non-existent worktree` | Unit | Error for missing registration |
| `updateLastSeen preserves all other fields unchanged` | Unit | Only lastSeenAt changes |
| `PUT /api/worktrees/{issueId} registers new worktree` | Integration | End-to-end registration via HTTP |
| `PUT /api/worktrees/{issueId} updates existing worktree` | Integration | Upsert semantics via HTTP |
| `PUT /api/worktrees/{issueId} returns 400 for malformed JSON` | Integration | Error handling for bad input |
| `PUT /api/worktrees/{issueId} returns 400 for missing fields` | Integration | Validation via HTTP |
| `PUT /api/worktrees/{issueId} returns 400 for invalid issueId` | Integration | Route validation |

---

## Files Changed

**8 files changed**, approximately **+600 insertions**

<details>
<summary>Full file list</summary>

**New Files (4):**
- `.iw/core/WorktreeRegistrationService.scala` - Pure business logic for registration
- `.iw/core/ServerClient.scala` - HTTP client with lazy server start
- `.iw/core/test/WorktreeRegistrationServiceTest.scala` - 12 unit tests
- `.iw/core/test/CaskServerTest.scala` - 5 integration tests

**Modified Files (4):**
- `.iw/core/CaskServer.scala` - Added PUT endpoint
- `.iw/commands/start.scala` - Added registration call after worktree creation
- `.iw/commands/open.scala` - Added updateLastSeen call at entry
- `.iw/commands/issue.scala` - Added updateLastSeen call at completion

</details>

---

## Key Implementation Details

### Best-Effort Registration Pattern

All CLI commands use this pattern for registration:

```scala
ServerClient.registerWorktree(...) match
  case Left(error) =>
    Output.warn(s"Failed to register worktree: $error")
  case Right(_) =>
    () // Silent success
```

Registration failures never change the command's exit code or prevent its primary function.

### Lazy Server Start

`ServerClient.ensureServerRunning` checks health first, then starts server in daemon thread if needed:

```scala
if isHealthy(port) then Right(())
else
  val serverThread = new Thread(() => CaskServer.start(...))
  serverThread.setDaemon(true)
  serverThread.start()
  if waitForServer(port, 5) then Right(())
  else Left("Server failed to start")
```

### Upsert Semantics

`WorktreeRegistrationService.register` handles both create and update:
- **New worktree**: Sets both `registeredAt` and `lastSeenAt` to now
- **Existing worktree**: Preserves `registeredAt`, updates all other fields including `lastSeenAt`

---

## Review Focus Areas

1. **Error Handling**: Verify all error paths return `Either`, no exceptions escape
2. **Best-Effort Philosophy**: Confirm registration failures don't break CLI commands
3. **Lazy Start**: Review daemon thread pattern for safety
4. **HTTP Client**: Compare with `LinearClient` pattern for consistency
5. **State Persistence**: Atomic writes via tmp + rename

---

**Ready for Review**
