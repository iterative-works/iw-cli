---
generated_from: 143859ba2ccc57dc795aa3fbfc24a04f33c884d5
generated_at: 2025-12-18T15:30:00Z
branch: IWLE-74-phase-02
issue_id: IWLE-74
phase: 2
files_analyzed:
  - iw-run
  - .iw/test/project-commands-execute.bats
---

# Review Packet: Phase 2 - Execute project command with ./ prefix

## Goals

Enable users to execute project-specific commands using the explicit `./` prefix syntax. This phase delivers the core value of the feature - actually running custom project commands with access to the shared core library.

**User Story:**
> As a developer using iw-cli, I want to execute project-specific commands using the ./ prefix so that I can run custom workflows specific to my project.

## Scenarios

- [x] Execute project command with `./` prefix successfully
- [x] Project command receives all CLI arguments correctly
- [x] Project command can import and use core library classes (Config, etc.)
- [x] Project command not found shows clear error with namespace indication
- [x] Shared command without prefix executes normally (no change to existing behavior)
- [x] Shared command not found shows clear error (distinct from project command errors)
- [x] Same name in both namespaces - each invoked independently without conflict
- [x] Invalid project command syntax (e.g., `./`, `./invalid$name`) shows error
- [x] Shared commands discover hooks from BOTH shared and project directories

## Entry Points

| File | Method/Function | Why Start Here |
|------|-----------------|----------------|
| `iw-run:190` | `execute_command()` | Core routing logic - detects `./` prefix and routes to correct namespace |
| `iw-run:199-221` | Project command branch | Project command execution path - validation, file lookup, scala-cli invocation |
| `iw-run:223-277` | Shared command branch | Shared command execution with enhanced hook discovery from both directories |
| `.iw/test/project-commands-execute.bats` | Test setup | E2E test infrastructure - understand how commands are tested |

## Diagrams

### Architecture Overview

```mermaid
graph TB
    subgraph "User Input"
        CLI["iw &lt;command&gt; [args]"]
    end

    subgraph "iw-run Script"
        MAIN["main()"]
        EXEC["execute_command()"]
        PREFIX{"Starts with ./?"}
        PROJECT["Project Command Path"]
        SHARED["Shared Command Path"]
    end

    subgraph "File System"
        PROJ_DIR["$PROJECT_DIR/.iw/commands/"]
        SHARED_DIR["$COMMANDS_DIR/"]
        CORE["$CORE_DIR/*.scala"]
    end

    subgraph "Execution"
        SCALA["scala-cli run"]
    end

    CLI --> MAIN
    MAIN --> EXEC
    EXEC --> PREFIX
    PREFIX -->|"./cmd"| PROJECT
    PREFIX -->|"cmd"| SHARED
    PROJECT --> PROJ_DIR
    PROJECT --> CORE
    SHARED --> SHARED_DIR
    SHARED --> CORE
    PROJ_DIR --> SCALA
    SHARED_DIR --> SCALA
    CORE --> SCALA
```

### Namespace Routing Flow

```mermaid
sequenceDiagram
    participant User
    participant iw-run
    participant FileSystem
    participant scala-cli

    User->>iw-run: iw ./deploy --env prod
    iw-run->>iw-run: Detect ./ prefix
    iw-run->>iw-run: Strip prefix → "deploy"
    iw-run->>iw-run: Validate command name
    iw-run->>FileSystem: Check $PROJECT_DIR/.iw/commands/deploy.scala
    FileSystem-->>iw-run: File exists
    iw-run->>scala-cli: run deploy.scala + core/*.scala -- --env prod
    scala-cli-->>User: Command output

    User->>iw-run: iw start ISSUE-123
    iw-run->>iw-run: No ./ prefix
    iw-run->>FileSystem: Check $COMMANDS_DIR/start.scala
    FileSystem-->>iw-run: File exists
    iw-run->>FileSystem: Find hooks: *.hook-start.scala (both dirs)
    FileSystem-->>iw-run: Hook files
    iw-run->>scala-cli: run start.scala + hooks + core/*.scala -- ISSUE-123
    scala-cli-->>User: Command output
```

### Hook Discovery (Enhanced for Shared Commands)

```mermaid
graph LR
    subgraph "Shared Command: iw doctor"
        SHARED_HOOKS["$COMMANDS_DIR/*.hook-doctor.scala"]
        PROJECT_HOOKS["$PROJECT_DIR/.iw/commands/*.hook-doctor.scala"]
    end

    subgraph "Hook Collection"
        MERGE["Combine hook files"]
    end

    subgraph "Execution"
        DOCTOR["doctor.scala + all hooks + core"]
    end

    SHARED_HOOKS --> MERGE
    PROJECT_HOOKS --> MERGE
    MERGE --> DOCTOR
```

### Error Handling Flow

```mermaid
flowchart TD
    INPUT["Command input"]
    PREFIX_CHECK{"Has ./ prefix?"}

    subgraph "Project Command"
        PROJ_VALIDATE{"Valid name?"}
        PROJ_EXISTS{"File exists?"}
        PROJ_ERROR1["Error: Invalid project command name"]
        PROJ_ERROR2["Error: Project command 'X' not found in .iw/commands/"]
        PROJ_EXEC["Execute project command"]
    end

    subgraph "Shared Command"
        SHARED_VALIDATE{"Valid name?"}
        SHARED_EXISTS{"File exists?"}
        SHARED_ERROR1["Error: Invalid command name"]
        SHARED_ERROR2["Error: Command 'X' not found"]
        SHARED_EXEC["Execute shared command"]
    end

    INPUT --> PREFIX_CHECK
    PREFIX_CHECK -->|Yes| PROJ_VALIDATE
    PREFIX_CHECK -->|No| SHARED_VALIDATE

    PROJ_VALIDATE -->|No| PROJ_ERROR1
    PROJ_VALIDATE -->|Yes| PROJ_EXISTS
    PROJ_EXISTS -->|No| PROJ_ERROR2
    PROJ_EXISTS -->|Yes| PROJ_EXEC

    SHARED_VALIDATE -->|No| SHARED_ERROR1
    SHARED_VALIDATE -->|Yes| SHARED_EXISTS
    SHARED_EXISTS -->|No| SHARED_ERROR2
    SHARED_EXISTS -->|Yes| SHARED_EXEC
```

## Test Summary

| Test | Type | Verifies |
|------|------|----------|
| `execute project command with ./ prefix successfully` | E2E | Basic project command execution |
| `project command receives CLI arguments correctly` | E2E | Argument passing through to project command |
| `project command can import core library (Config)` | E2E | Core library availability for project commands |
| `project command not found shows clear error` | E2E | Namespace-specific error messages |
| `shared command without prefix executes normally` | E2E | No regression to existing shared commands |
| `shared command not found shows clear error` | E2E | Distinct error message for shared namespace |
| `same name in both namespaces - each invoked correctly` | E2E | Namespace isolation |
| `invalid project command syntax ./ alone shows error` | E2E | Validation for empty command name |
| `invalid project command syntax with special chars shows error` | E2E | Validation for invalid characters |
| `shared command discovers hooks from project directory` | E2E | Enhanced hook discovery |

**Test Coverage:**
- 10 E2E tests covering all acceptance criteria
- 79 total non-bootstrap tests passing (no regressions)

## Files Changed

**Summary:** 2 code files changed, 1 test file added

| Status | File | Description |
|--------|------|-------------|
| M | `iw-run` | Updated `execute_command()` with namespace routing |
| A | `.iw/test/project-commands-execute.bats` | E2E tests for project command execution |

<details>
<summary>Full file list (from main...HEAD)</summary>

- `.iw/test/project-commands-execute.bats` (A) - E2E tests for Phase 2
- `.iw/test/project-commands-list.bats` (A) - E2E tests for Phase 1
- `iw-run` (M) - Main implementation
- `project-management/issues/IWLE-74/analysis.md` (A)
- `project-management/issues/IWLE-74/implementation-log.md` (A)
- `project-management/issues/IWLE-74/phase-01-context.md` (A)
- `project-management/issues/IWLE-74/phase-01-tasks.md` (A)
- `project-management/issues/IWLE-74/phase-02-context.md` (A)
- `project-management/issues/IWLE-74/phase-02-tasks.md` (A)
- `project-management/issues/IWLE-74/review-packet-phase-01.md` (A)
- `project-management/issues/IWLE-74/review-phase-01-2025-12-18-143301.md` (A)
- `project-management/issues/IWLE-74/review-phase-02-2025-12-18.md` (A)
- `project-management/issues/IWLE-74/tasks.md` (A)

</details>

## Key Implementation Details

### Namespace Routing Logic

```bash
# iw-run:199-222
if [[ "$cmd_name" == ./* ]]; then
    # Project command - strip ./ prefix
    is_project_cmd=true
    actual_name="${cmd_name:2}"
    # Validate and execute from $PROJECT_DIR/.iw/commands/
else
    # Shared command - use existing $COMMANDS_DIR
```

### Core Library Inclusion

Project commands include the core library for imports:
```bash
exec scala-cli run "$cmd_file" "$CORE_DIR"/*.scala -- "$@"
```

### Enhanced Hook Discovery

Shared commands now discover hooks from both directories:
```bash
# Find shared hooks
shared_hooks=$(find "$COMMANDS_DIR" -name "*.hook-${actual_name}.scala")
# Find project hooks
project_hooks=$(find "$PROJECT_DIR/.iw/commands" -name "*.hook-${actual_name}.scala")
# Combine and include all
```

## Code Review Notes

- **Code review file:** `review-phase-02-2025-12-18.md`
- **Iterations:** 1 (passed)
- **Critical issues:** None
- **Warnings:** 2 (minor, documented)
