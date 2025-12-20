---
generated_from: c4ea937cc4d4920ab4b7bcbfd6d3da7d7eb12ffe
generated_at: 2025-12-20T11:30:00Z
branch: IWLE-100-phase-03
issue_id: IWLE-100
phase: 3
files_analyzed:
  - .iw/commands/server.scala
  - .iw/commands/server-daemon.scala
  - .iw/core/ServerConfig.scala
  - .iw/core/ServerConfigRepository.scala
  - .iw/core/ServerStatus.scala
  - .iw/core/ServerLifecycleService.scala
  - .iw/core/ProcessManager.scala
  - .iw/core/CaskServer.scala
  - .iw/core/ServerClient.scala
  - .iw/commands/dashboard.scala
---

# Review Packet: Phase 3 - Server lifecycle management

**Issue:** IWLE-100
**Phase:** 3 of 7
**Story:** Story 6 - Server lifecycle management

## Goals

This phase solidifies the infrastructure by adding explicit control over the dashboard server lifecycle:

1. **Explicit server control**: Add `iw server start`, `iw server stop`, and `iw server status` commands
2. **Background daemon mode**: Server runs as a background process with PID file tracking
3. **Process management**: Spawn background process, manage PID file, detect running state
4. **Health check integration**: Verify server is responding before declaring success
5. **Graceful shutdown**: Clean shutdown that removes PID file and terminates process
6. **Port configuration**: Move from hardcoded port to config file at `~/.local/share/iw/server/config.json`
7. **Status reporting**: Show server state, port, worktree count, uptime

## Scenarios

- [ ] User runs `iw server start` and server starts in background with PID file
- [ ] User runs `iw server start` again and gets "already running" error
- [ ] User runs `iw server stop` and server shuts down gracefully
- [ ] User runs `iw server stop` when not running and gets "not running" message
- [ ] User runs `iw server status` and sees port, worktree count, uptime, PID
- [ ] User runs `iw server status` when stopped and sees "not running"
- [ ] User runs `iw dashboard` and auto-start uses configured port
- [ ] User creates custom port config and server uses that port
- [ ] Stale PID file is detected and handled (allows new server start)
- [ ] `GET /api/status` endpoint returns JSON with server runtime info

## Entry Points

| File | Method/Class | Why Start Here |
|------|--------------|----------------|
| `.iw/commands/server.scala` | `main()` | CLI entry point with start/stop/status subcommands |
| `.iw/core/ProcessManager.scala` | `ProcessManager` | Core process lifecycle: spawn, stop, PID management |
| `.iw/core/ServerConfigRepository.scala` | `ServerConfigRepository` | Port configuration persistence |
| `.iw/core/CaskServer.scala` | `@cask.get("/api/status")` | New status endpoint for runtime info |
| `.iw/core/ServerLifecycleService.scala` | `ServerLifecycleService` | Pure business logic for uptime formatting |

## Diagrams

### Architecture Overview

```mermaid
graph TB
    subgraph CLI["CLI Layer"]
        server["iw server (start|stop|status)"]
        dashboard["iw dashboard"]
    end

    subgraph Core["Core Layer"]
        config["ServerConfig"]
        status["ServerStatus"]
        lifecycle["ServerLifecycleService"]
    end

    subgraph Infrastructure["Infrastructure Layer"]
        configRepo["ServerConfigRepository"]
        processManager["ProcessManager"]
        caskServer["CaskServer"]
        serverClient["ServerClient"]
    end

    subgraph External["External"]
        configFile["config.json"]
        pidFile["server.pid"]
        stateFile["state.json"]
        httpServer["HTTP :9876"]
    end

    server --> config
    server --> processManager
    server --> configRepo
    server --> lifecycle

    dashboard --> configRepo
    dashboard --> serverClient

    configRepo --> configFile
    processManager --> pidFile
    caskServer --> stateFile
    caskServer --> httpServer

    serverClient --> httpServer
```

### Component Relationships

```mermaid
classDiagram
    class ServerConfig {
        +port: Int
        +validate(port): Either
        +default: ServerConfig
    }

    class ServerStatus {
        +running: Boolean
        +port: Int
        +worktreeCount: Int
        +startedAt: Option~Instant~
        +pid: Option~Long~
    }

    class ServerLifecycleService {
        +formatUptime(startedAt, now): String
        +createStatus(state, startedAt, pid, port): ServerStatus
    }

    class ServerConfigRepository {
        +read(path): Either~String, ServerConfig~
        +write(config, path): Either~String, Unit~
        +getOrCreateDefault(path): Either~String, ServerConfig~
    }

    class ProcessManager {
        +writePidFile(pid, path): Either
        +readPidFile(path): Either~Option~Long~~
        +isProcessAlive(pid): Boolean
        +stopProcess(pid, timeout): Either
        +spawnServerProcess(statePath, port): Either~Long~
    }

    class CaskServer {
        -startedAt: Instant
        -port: Int
        +/api/status: JSON
        +start(statePath, port): Unit
    }

    ServerLifecycleService --> ServerStatus
    ServerLifecycleService --> ServerConfig
    ServerConfigRepository --> ServerConfig
    CaskServer --> ServerStatus
```

### Server Start Flow

```mermaid
sequenceDiagram
    participant User
    participant CLI as server.scala
    participant ConfigRepo as ServerConfigRepository
    participant ProcMgr as ProcessManager
    participant Daemon as server-daemon
    participant Server as CaskServer

    User->>CLI: iw server start
    CLI->>ConfigRepo: getOrCreateDefault()
    ConfigRepo-->>CLI: ServerConfig(port)

    CLI->>ProcMgr: readPidFile()
    alt PID exists and process alive
        CLI-->>User: "Already running"
    else PID stale or missing
        CLI->>ProcMgr: spawnServerProcess(statePath, port)
        ProcMgr->>Daemon: ProcessBuilder.start()
        Daemon->>Server: CaskServer.start()
        ProcMgr-->>CLI: pid

        CLI->>ProcMgr: writePidFile(pid)

        loop Health check (max 5s)
            CLI->>Server: GET /health
            Server-->>CLI: 200 OK
        end

        CLI-->>User: "Server started on http://localhost:9876"
    end
```

### Server Stop Flow

```mermaid
sequenceDiagram
    participant User
    participant CLI as server.scala
    participant ProcMgr as ProcessManager
    participant Server as CaskServer

    User->>CLI: iw server stop
    CLI->>ProcMgr: readPidFile()

    alt No PID file
        CLI-->>User: "Server is not running"
    else PID found
        CLI->>ProcMgr: isProcessAlive(pid)
        alt Process dead (stale PID)
            CLI->>ProcMgr: removePidFile()
            CLI-->>User: "Server is not running (stale PID file)"
        else Process alive
            CLI->>ProcMgr: stopProcess(pid, 10s)
            ProcMgr->>Server: SIGTERM
            Server-->>ProcMgr: exits
            CLI->>ProcMgr: removePidFile()
            CLI-->>User: "Server stopped"
        end
    end
```

### Layer Architecture (FCIS)

```mermaid
graph TB
    subgraph Presentation["Presentation Layer (Commands)"]
        serverCmd["server.scala"]
        dashboardCmd["dashboard.scala"]
        daemonCmd["server-daemon.scala"]
    end

    subgraph Application["Application Layer (Pure)"]
        lifecycleSvc["ServerLifecycleService"]
    end

    subgraph Domain["Domain Layer (Pure)"]
        config["ServerConfig"]
        status["ServerStatus"]
    end

    subgraph Infrastructure["Infrastructure Layer (Effects)"]
        configRepo["ServerConfigRepository"]
        procMgr["ProcessManager"]
        serverClient["ServerClient"]
        caskServer["CaskServer"]
    end

    Presentation --> Application
    Presentation --> Infrastructure
    Application --> Domain
    Infrastructure --> Domain

    style Domain fill:#e8f5e9
    style Application fill:#e3f2fd
    style Infrastructure fill:#fff3e0
    style Presentation fill:#fce4ec
```

## Test Summary

| Test | Type | Verifies |
|------|------|----------|
| `ServerConfigTest."Valid port numbers parse correctly"` | Unit | Port validation accepts 1024-65535 |
| `ServerConfigTest."Invalid port 0 fails validation"` | Unit | Port 0 rejected |
| `ServerConfigTest."Invalid port 70000 fails validation"` | Unit | Port above 65535 rejected |
| `ServerConfigTest."Port below 1024 fails validation"` | Unit | Privileged ports rejected |
| `ServerConfigRepositoryTest."Write config file and read back"` | Integration | Config persistence round-trip |
| `ServerConfigRepositoryTest."Create default config if file missing"` | Integration | Default config with port 9876 |
| `ServerConfigRepositoryTest."Handle invalid JSON in config file"` | Integration | Graceful error handling |
| `ServerConfigRepositoryTest."Handle missing parent directory"` | Integration | Directory creation |
| `ServerStatusTest."Create ServerStatus with all fields"` | Unit | Status model construction |
| `ServerStatusTest."ServerStatus JSON serialization"` | Unit | Upickle serialization |
| `ServerLifecycleServiceTest."Format uptime for seconds only"` | Unit | "45s" format |
| `ServerLifecycleServiceTest."Format uptime for minutes and seconds"` | Unit | "5m 12s" format |
| `ServerLifecycleServiceTest."Format uptime for hours and minutes"` | Unit | "2h 34m" format |
| `ServerLifecycleServiceTest."Format uptime for days"` | Unit | "1d 1h" format |
| `ServerLifecycleServiceTest."Create status for running server"` | Unit | Status creation from state |
| `ProcessManagerTest."Write PID file and read back"` | Integration | PID file round-trip |
| `ProcessManagerTest."Check if current process is alive"` | Integration | ProcessHandle.isAlive() |
| `ProcessManagerTest."Detect process is not alive for invalid PID"` | Integration | Invalid PID detection |
| `ProcessManagerTest."Read non-existent PID file returns None"` | Integration | Missing file handling |
| `ProcessManagerTest."Remove PID file after write"` | Integration | PID cleanup |
| `ProcessManagerTest."Handle malformed PID file content"` | Integration | Error for non-numeric content |
| `CaskServerTest."GET /api/status returns 200 OK with status JSON"` | Integration | Status endpoint works |
| `CaskServerTest."GET /api/status shows correct worktree count"` | Integration | Worktree count in status |
| `CaskServerTest."GET /api/status startedAt is recent"` | Integration | Timestamp accuracy |

**Test Counts:**
- Unit tests: 14 tests
- Integration tests: 21 tests
- Total: 35 new tests

## Files Changed

**12 files changed** (11 new, 4 modified)

<details>
<summary>Full file list</summary>

**New Files:**
- `.iw/commands/server.scala` (A) - CLI with start/stop/status subcommands
- `.iw/commands/server-daemon.scala` (A) - Background server entry point
- `.iw/core/ServerConfig.scala` (A) - Port configuration domain model
- `.iw/core/ServerConfigRepository.scala` (A) - Config file persistence
- `.iw/core/ServerStatus.scala` (A) - Server status domain model
- `.iw/core/ServerLifecycleService.scala` (A) - Pure lifecycle business logic
- `.iw/core/ProcessManager.scala` (A) - Process spawning and PID management
- `.iw/core/test/ServerConfigTest.scala` (A) - 7 unit tests
- `.iw/core/test/ServerConfigRepositoryTest.scala` (A) - 8 integration tests
- `.iw/core/test/ServerStatusTest.scala` (A) - 3 unit tests
- `.iw/core/test/ServerLifecycleServiceTest.scala` (A) - 8 unit tests
- `.iw/core/test/ProcessManagerTest.scala` (A) - 9 integration tests

**Modified Files:**
- `.iw/core/CaskServer.scala` (M) - Added `startedAt`, `port`, `/api/status` endpoint
- `.iw/core/ServerClient.scala` (M) - Reads port from config file
- `.iw/commands/dashboard.scala` (M) - Uses config file for port
- `.iw/core/test/CaskServerTest.scala` (M) - Added 3 tests for status endpoint

</details>

## Review Checklist

Before approving, verify:

- [ ] All hardcoded port 9876 references removed (except as default in config creation)
- [ ] PID file cleanup happens in all shutdown paths (graceful stop, error cases)
- [ ] Health checks use configured port, not hardcoded
- [ ] Error messages are user-friendly and actionable
- [ ] Process spawning redirects stdout/stderr appropriately (not lost)
- [ ] Stale PID detection works correctly (doesn't prevent start)
- [ ] FCIS pattern maintained (pure domain/application, effects in infrastructure)
- [ ] Unit tests cover edge cases (invalid port, missing config, stale PID)
- [ ] Integration tests clean up resources (no orphaned processes or files)
