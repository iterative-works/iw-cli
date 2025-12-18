---
generated_from: 2ca4c60affadafc8a9e6f25ab261d619055ff04d
generated_at: 2025-12-18T16:00:00Z
branch: IWLE-74-phase-03
issue_id: IWLE-74
phase: 3
files_analyzed:
  - iw-run
  - .iw/test/project-commands-describe.bats
---

# Review Packet: Phase 3 - Describe project command with `./` prefix

## Goals

Enable users to get detailed help for project-specific commands using the `./` prefix syntax with `--describe`. This completes the feature by providing discoverability for project commands.

**User Story:**
> As a developer using iw-cli, I want to describe project-specific commands using the ./ prefix so that I can see detailed help for my project's commands.

## Scenarios

- [x] Describe project command shows full metadata (PURPOSE, USAGE, ARGS, EXAMPLES)
- [x] Describe project command with minimal metadata shows what's available
- [x] Describe project command not found shows clear error with namespace indication
- [x] Describe shared command (no prefix) works normally (no regression)
- [x] Invalid project command syntax (e.g., `./`, `./invalid$name`) shows error
- [x] Command names with dashes work correctly

## Entry Points

| File | Method/Function | Why Start Here |
|------|-----------------|----------------|
| `iw-run:122` | `describe_command()` | Core routing logic - detects `./` prefix and routes to correct namespace |
| `iw-run:125-148` | Project command branch | Project command description path - validation, file lookup, display |
| `iw-run:149-162` | Shared command branch | Shared command description (unchanged, for comparison) |
| `.iw/test/project-commands-describe.bats` | Test setup/scenarios | E2E test infrastructure - understand how describe is tested |

## Diagrams

### Architecture Overview

```mermaid
graph TB
    subgraph "User Input"
        CLI["iw --describe &lt;command&gt;"]
    end

    subgraph "iw-run Script"
        MAIN["main()"]
        DESC["describe_command()"]
        PREFIX{"Starts with ./?"}
        PROJECT["Project Command Path"]
        SHARED["Shared Command Path"]
        PARSE["parse_command_header()"]
    end

    subgraph "File System"
        PROJ_DIR["$PROJECT_DIR/.iw/commands/"]
        SHARED_DIR["$COMMANDS_DIR/"]
    end

    subgraph "Output"
        DISPLAY["Display metadata"]
    end

    CLI --> MAIN
    MAIN --> DESC
    DESC --> PREFIX
    PREFIX -->|"./cmd"| PROJECT
    PREFIX -->|"cmd"| SHARED
    PROJECT --> PROJ_DIR
    SHARED --> SHARED_DIR
    PROJ_DIR --> PARSE
    SHARED_DIR --> PARSE
    PARSE --> DISPLAY
```

### Describe Command Flow

```mermaid
sequenceDiagram
    participant User
    participant iw-run
    participant FileSystem
    participant Output

    User->>iw-run: iw --describe ./deploy
    iw-run->>iw-run: Detect ./ prefix
    iw-run->>iw-run: Strip prefix → "deploy"
    iw-run->>iw-run: Validate command name
    iw-run->>FileSystem: Check $PROJECT_DIR/.iw/commands/deploy.scala
    FileSystem-->>iw-run: File exists
    iw-run->>iw-run: parse_command_header() for PURPOSE
    iw-run->>iw-run: parse_command_header() for USAGE
    iw-run->>iw-run: parse_command_header() for ARGS
    iw-run->>iw-run: parse_command_header() for EXAMPLES
    iw-run->>Output: Display "=== Command: ./deploy ==="
    iw-run->>Output: Display Purpose, Usage, Args, Examples
    Output-->>User: Formatted help output
```

### Error Handling Flow

```mermaid
flowchart TD
    INPUT["--describe command"]
    PREFIX_CHECK{"Has ./ prefix?"}

    subgraph "Project Command"
        PROJ_VALIDATE{"Valid name?"}
        PROJ_EXISTS{"File exists?"}
        PROJ_ERROR1["Error: Invalid project command name"]
        PROJ_ERROR2["Error: Project command 'X' not found in .iw/commands/"]
        PROJ_DESC["Display with ./ prefix in header"]
    end

    subgraph "Shared Command"
        SHARED_VALIDATE{"Valid name?"}
        SHARED_EXISTS{"File exists?"}
        SHARED_ERROR1["Error: Invalid command name"]
        SHARED_ERROR2["Error: Command 'X' not found"]
        SHARED_DESC["Display without prefix in header"]
    end

    INPUT --> PREFIX_CHECK
    PREFIX_CHECK -->|Yes| PROJ_VALIDATE
    PREFIX_CHECK -->|No| SHARED_VALIDATE

    PROJ_VALIDATE -->|No| PROJ_ERROR1
    PROJ_VALIDATE -->|Yes| PROJ_EXISTS
    PROJ_EXISTS -->|No| PROJ_ERROR2
    PROJ_EXISTS -->|Yes| PROJ_DESC

    SHARED_VALIDATE -->|No| SHARED_ERROR1
    SHARED_VALIDATE -->|Yes| SHARED_EXISTS
    SHARED_EXISTS -->|No| SHARED_ERROR2
    SHARED_EXISTS -->|Yes| SHARED_DESC
```

### Feature Complete Overview (All 3 Phases)

```mermaid
graph LR
    subgraph "Phase 1: Discovery"
        LIST["iw --list"]
        LIST_OUT["Shows both namespaces"]
    end

    subgraph "Phase 2: Execution"
        EXEC["iw ./cmd"]
        EXEC_OUT["Runs project command"]
    end

    subgraph "Phase 3: Description"
        DESC["iw --describe ./cmd"]
        DESC_OUT["Shows project help"]
    end

    LIST --> LIST_OUT
    EXEC --> EXEC_OUT
    DESC --> DESC_OUT
```

## Test Summary

| Test | Type | Verifies |
|------|------|----------|
| `describe project command shows full metadata` | E2E | All metadata fields (PURPOSE, USAGE, ARGS, EXAMPLES) displayed |
| `describe project command with minimal metadata` | E2E | Graceful handling of commands with only PURPOSE/USAGE |
| `describe project command not found shows clear error` | E2E | Namespace-specific error message |
| `describe shared command (no prefix) works normally` | E2E | No regression to existing shared command describe |
| `describe invalid project command syntax ./ alone` | E2E | Validation for empty command name |
| `describe invalid project command syntax with special chars` | E2E | Validation for invalid characters |
| `describe project command with dashes in name works` | E2E | Support for command names with dashes |

**Test Coverage:**
- 7 E2E tests covering all acceptance criteria
- 86 total non-bootstrap tests passing (no regressions)

## Files Changed

**Summary:** 2 code files changed, 1 test file added

| Status | File | Description |
|--------|------|-------------|
| M | `iw-run` | Updated `describe_command()` with namespace routing |
| A | `.iw/test/project-commands-describe.bats` | E2E tests for project command description |

<details>
<summary>Full file list (Phase 3 specific)</summary>

- `.iw/test/project-commands-describe.bats` (A) - E2E tests for Phase 3
- `iw-run` (M) - describe_command() updated
- `project-management/issues/IWLE-74/implementation-log.md` (M) - Phase 3 entry added
- `project-management/issues/IWLE-74/phase-03-context.md` (A) - Phase context
- `project-management/issues/IWLE-74/phase-03-tasks.md` (A) - Phase tasks
- `project-management/issues/IWLE-74/review-phase-03-2025-12-18.md` (A) - Code review

</details>

## Key Implementation Details

### Namespace Routing Logic (same pattern as execute_command)

```bash
# iw-run:125-148
if [[ "$cmd_name" == ./* ]]; then
    # Project command - strip ./ prefix
    local actual_name="${cmd_name:2}"
    # Validate
    if [[ -z "$actual_name" ]] || [[ ! "$actual_name" =~ ^[a-zA-Z0-9_-]+$ ]]; then
        echo "Error: Invalid project command name '$cmd_name'" >&2
        exit 1
    fi
    cmd_file="$PROJECT_DIR/.iw/commands/${actual_name}.scala"
    display_name="./$actual_name"
    # ... check file exists and display
```

### Output Header Shows Namespace

Project commands display with `./` prefix:
```
=== Command: ./deploy ===

Purpose:
Deploy application to production environment
...
```

Shared commands display without prefix:
```
=== Command: version ===

Purpose:
Show version information
...
```

## Code Review Notes

- **Code review file:** `review-phase-03-2025-12-18.md`
- **Iterations:** 1 (passed)
- **Critical issues:** None
- **Warnings:** 1 (acceptable - shell logic tested via E2E)

## Feature Completion

This is the **final phase** of IWLE-74. With Phase 3 complete, the full feature set is:

| Capability | Command | Status |
|------------|---------|--------|
| Discovery | `iw --list` | ✅ Phase 1 |
| Execution | `iw ./cmd` | ✅ Phase 2 |
| Description | `iw --describe ./cmd` | ✅ Phase 3 |

All phases use consistent `./` prefix for project commands, following the explicit namespacing design decision.
