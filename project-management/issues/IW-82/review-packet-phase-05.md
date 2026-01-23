---
generated_from: 09edf7c61cc958ce4e4e2b215d305cb9a97f2997
generated_at: 2026-01-23T13:56:55Z
branch: IW-82
issue_id: IW-82
phase: 5
files_analyzed:
  - .iw/test/dashboard-dev-mode.bats
  - .iw/commands/dashboard.scala
  - project-management/issues/IW-82/phase-05-tasks.md
---

# Phase 5: Validate Development Mode Isolation

## Goals

This phase validates that development mode (`--dev` flag) provides complete isolation from production data. The goal is to ensure production state and config files are never modified when using `--dev` mode.

Key objectives:
- Verify production state file remains untouched in dev mode
- Verify production config file remains untouched in dev mode
- Validate dev mode creates isolated temporary directory
- Document isolation guarantees in CLI help text

## Scenarios

- [ ] Dev mode creates temporary directory with timestamp
- [ ] Dev mode creates state.json in temp directory
- [ ] Dev mode creates config.json in temp directory
- [ ] Production state file unchanged after dev mode operations
- [ ] Production config file unchanged after dev mode operations
- [ ] Dev mode enables sample data by default
- [ ] Help text clearly documents isolation guarantees

## Entry Points

Start your review from these locations:

| File | Method/Class | Why Start Here |
|------|--------------|----------------|
| `.iw/test/dashboard-dev-mode.bats` | Test suite | E2E validation tests for dev mode isolation |
| `.iw/commands/dashboard.scala` | `printHelp()` | Documentation of isolation guarantees |
| `.iw/commands/dashboard.scala` | Dev mode setup (lines 48-78) | Temp directory and config creation logic |

## Test Architecture

This diagram shows how the E2E tests validate isolation:

```mermaid
flowchart TB
    subgraph "Test Setup"
        T[BATS Test] --> B[Create Baseline State]
        B --> H[Record SHA256 Hash]
    end

    subgraph "Dev Mode Execution"
        H --> S[Start Server with --dev]
        S --> TD[Create /tmp/iw-dev-timestamp/]
        TD --> TS[Create temp state.json]
        TD --> TC[Create temp config.json]
        TC --> O[Perform Operations]
    end

    subgraph "Validation"
        O --> K[Kill Server]
        K --> V[Verify Production Hash]
        V --> A{Hash Match?}
        A -->|Yes| P[Test PASS]
        A -->|No| F[Test FAIL]
    end

    style TD fill:#e1f5e1
    style TS fill:#e1f5e1
    style TC fill:#e1f5e1
    style P fill:#90ee90
    style F fill:#ffcccb
```

**Key points for reviewer:**
- Tests verify byte-for-byte integrity of production files
- Dev mode creates timestamped temp directories for complete isolation
- All tests use SHA256 hashing to detect any production file changes

## Test Summary

| Test | Type | Verifies |
|------|------|----------|
| `dev mode creates temp directory` | E2E | Dev mode creates `/tmp/iw-dev-*` directory |
| `dev mode creates state.json in temp directory` | E2E | State file exists in temp directory, not production |
| `dev mode creates config.json in temp directory` | E2E | Config file exists in temp directory, not production |
| `production state file unchanged after dev mode` | E2E | Production state hash identical before/after dev mode |
| `production config file unchanged after dev mode` | E2E | Production config hash identical before/after dev mode |
| `dev mode enables sample data by default` | E2E | Sample data automatically loaded in dev mode |

Coverage: 6 E2E tests covering complete isolation of dev mode from production state

**Test Results:**
```
1..6
ok 1 dev mode creates temp directory
ok 2 dev mode creates state.json in temp directory
ok 3 dev mode creates config.json in temp directory
ok 4 production state file unchanged after dev mode
ok 5 production config file unchanged after dev mode
ok 6 dev mode enables sample data by default
```

All tests passing ✅

## Isolation Architecture

This diagram shows how dev mode isolates production from development:

```mermaid
flowchart LR
    subgraph prod["Production Environment"]
        PS[~/.local/share/iw/server/<br/>state.json]
        PC[~/.local/share/iw/server/<br/>config.json]
    end

    subgraph dev["Dev Mode Environment"]
        DT["/tmp/iw-dev-TIMESTAMP/"]
        DS[state.json<br/>with sample data]
        DC[config.json<br/>port: dynamic]
        DT --> DS
        DT --> DC
    end

    CMD[./iw dashboard --dev]
    CMD -->|creates| DT
    CMD -.->|never touches| PS
    CMD -.->|never touches| PC

    style PS fill:#ffebcc
    style PC fill:#ffebcc
    style DS fill:#e1f5e1
    style DC fill:#e1f5e1
    style DT fill:#e1f5e1
```

**Key points for reviewer:**
- Solid arrows show actual file operations
- Dashed arrows show files that are never accessed
- Temp directory uses timestamp to avoid conflicts
- Production files (orange) remain completely untouched

## Documentation Added

The CLI help text now includes comprehensive isolation guarantees:

**Help Output Sections:**
1. **Development Mode Description**: Explains what `--dev` does
2. **Isolation Guarantees**: Explicit promises about production safety
3. **Examples**: Usage patterns for different scenarios

**Key Documentation Points:**
- Production state file is never read or written in dev mode
- Production config file is never modified in dev mode
- All operations happen in isolated temporary directory
- Safe to experiment without affecting real worktree registrations

Review the `printHelp()` function in `dashboard.scala` (lines 183-214) for full documentation.

## Files Changed

**3 files** changed, +218 insertions, -9 deletions

<details>
<summary>Full file list</summary>

- `.iw/test/dashboard-dev-mode.bats` (A) +180 lines - E2E test suite for dev mode isolation
- `.iw/commands/dashboard.scala` (M) +38 -9 lines - Added help text and help flag support
- `project-management/issues/IW-82/phase-05-tasks.md` (M) - Task tracking updates

</details>

## Review Checklist

Use this checklist while reviewing:

**E2E Tests:**
- [ ] Tests use proper setup/teardown to avoid pollution
- [ ] SHA256 hashing correctly verifies byte-for-byte integrity
- [ ] Tests handle server lifecycle (start, wait, kill) properly
- [ ] Temp directory cleanup happens in teardown
- [ ] All 6 tests are independent and can run in any order

**Documentation:**
- [ ] Help text clearly states isolation guarantees
- [ ] Examples demonstrate both dev and production modes
- [ ] Warning about production file locations is prominent
- [ ] Help flag (`--help`, `-h`) works correctly

**Isolation Logic:**
- [ ] Dev mode creates timestamped temp directory
- [ ] Config file is created in temp directory (not just state)
- [ ] Production paths are never accessed in dev mode
- [ ] Sample data is auto-enabled in dev mode

