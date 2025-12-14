---
generated_from: cec1925663e2ea2fb623e4ea1b76aa49421edc94
generated_at: 2025-12-13T23:15:00Z
branch: IWLE-72-phase-04
issue_id: IWLE-72
phase: 04
files_analyzed:
  - .iw/core/IssueId.scala
  - .iw/core/WorktreePath.scala
  - .iw/core/Tmux.scala
  - .iw/core/GitWorktree.scala
  - .iw/core/Process.scala
  - .iw/commands/start.scala
  - .iw/core/test/IssueIdTest.scala
  - .iw/core/test/WorktreePathTest.scala
  - .iw/core/test/TmuxAdapterTest.scala
  - .iw/core/test/GitWorktreeAdapterTest.scala
  - .iw/test/start.bats
---

# Review Packet: Phase 4 - Create worktree for issue with tmux session

**Issue:** IWLE-72
**Phase:** 4 of 7
**Branch:** IWLE-72-phase-04

---

## Goals

This phase implements the `iw start <issue-id>` command that creates an isolated worktree for a specific issue and launches a tmux session for it.

**Primary Objectives:**
- Create sibling worktree named `{project}-{ISSUE-ID}` (e.g., `kanon-IWLE-123`)
- Create git branch matching the issue ID
- Create tmux session with the same name as the worktree
- Attach the user to the tmux session with working directory in the new worktree
- Handle edge cases: existing worktree, existing directory, existing branch

---

## Scenarios

End-to-end scenarios to verify during review:

- [ ] `./iw start IWLE-123` creates worktree `{project}-IWLE-123` as sibling directory
- [ ] Worktree has git branch named `IWLE-123`
- [ ] Tmux session `{project}-IWLE-123` is created
- [ ] User is attached to the tmux session
- [ ] Working directory in session is the new worktree
- [ ] `./iw start IWLE-123` with existing directory shows error and exits
- [ ] `./iw start IWLE-123` with existing worktree suggests using `./iw open`
- [ ] `./iw start IWLE-123` with existing tmux session suggests using `./iw open`
- [ ] `./iw start` without arguments shows usage error
- [ ] `./iw start invalid-123` shows invalid format error
- [ ] `./iw start IWLE-123` without config suggests running `./iw init`
- [ ] `./iw start IWLE-123` with existing branch uses the branch (no error)
- [ ] Lowercase input `iwle-123` is converted to uppercase

---

## Entry Points

Start code review from these files:

| File | Method/Class | Why Start Here |
|------|--------------|----------------|
| `.iw/commands/start.scala` | `start()` | CLI entry point - orchestrates the entire worktree creation workflow |
| `.iw/core/IssueId.scala` | `IssueId.parse()` | Domain validation - validates issue ID format (PROJECT-123) |
| `.iw/core/WorktreePath.scala` | `WorktreePath` | Domain calculation - computes sibling directory paths and session names |
| `.iw/core/GitWorktree.scala` | `GitWorktreeAdapter` | Infrastructure - git worktree operations |
| `.iw/core/Tmux.scala` | `TmuxAdapter` | Infrastructure - tmux session management |

---

## Diagrams

### Architecture Overview

```mermaid
graph TB
    subgraph "User"
        CLI[iw start IWLE-123]
    end

    subgraph "Domain Layer"
        IssueId[IssueId]
        WorktreePath[WorktreePath]
    end

    subgraph "Infrastructure Layer"
        GitWorktree[GitWorktreeAdapter]
        Tmux[TmuxAdapter]
        Process[ProcessAdapter]
    end

    subgraph "External Systems"
        Git[(Git)]
        TmuxServer[(Tmux Server)]
    end

    CLI --> IssueId
    CLI --> WorktreePath
    CLI --> GitWorktree
    CLI --> Tmux

    GitWorktree --> Process
    Tmux --> Process
    Process --> Git
    Process --> TmuxServer
```

### Component Relationships

```mermaid
classDiagram
    class IssueId {
        +value: String
        +toBranchName(): String
        +parse(raw: String): Either[String, IssueId]
    }

    class WorktreePath {
        +projectName: String
        +issueId: IssueId
        +directoryName: String
        +sessionName: String
        +resolve(currentDir: Path): Path
    }

    class TmuxAdapter {
        +sessionExists(name: String): Boolean
        +createSession(name: String, workDir: Path): Either[String, Unit]
        +attachSession(name: String): Either[String, Unit]
        +killSession(name: String): Either[String, Unit]
    }

    class GitWorktreeAdapter {
        +worktreeExists(path: Path, workDir: Path): Boolean
        +branchExists(branchName: String, workDir: Path): Boolean
        +createWorktree(path: Path, branch: String, workDir: Path): Either[String, Unit]
        +createWorktreeForBranch(path: Path, branch: String, workDir: Path): Either[String, Unit]
    }

    class ProcessAdapter {
        +run(command: Seq[String]): ProcessResult
    }

    WorktreePath --> IssueId
    TmuxAdapter --> ProcessAdapter
    GitWorktreeAdapter --> ProcessAdapter
```

### Start Command Flow

```mermaid
sequenceDiagram
    participant User
    participant start.scala
    participant IssueId
    participant ConfigRepo
    participant WorktreePath
    participant GitWorktree
    participant Tmux

    User->>start.scala: ./iw start IWLE-123

    start.scala->>IssueId: parse("IWLE-123")
    IssueId-->>start.scala: Right(IssueId)

    start.scala->>ConfigRepo: read()
    ConfigRepo-->>start.scala: Some(config)

    start.scala->>WorktreePath: new(projectName, issueId)
    WorktreePath-->>start.scala: worktreePath

    start.scala->>GitWorktree: worktreeExists(path)?
    GitWorktree-->>start.scala: false

    start.scala->>Tmux: sessionExists(name)?
    Tmux-->>start.scala: false

    start.scala->>GitWorktree: branchExists(branch)?
    GitWorktree-->>start.scala: false

    start.scala->>GitWorktree: createWorktree(path, branch)
    GitWorktree-->>start.scala: Right(())

    start.scala->>Tmux: createSession(name, path)
    Tmux-->>start.scala: Right(())

    start.scala->>Tmux: attachSession(name)
    Tmux-->>start.scala: Right(())

    start.scala-->>User: Attached to tmux session
```

### Layer Diagram (FCIS)

```mermaid
graph TB
    subgraph "Presentation Layer"
        Commands[".iw/commands/"]
        start[start.scala]
    end

    subgraph "Domain Layer (Pure)"
        IssueId[IssueId.scala]
        WorktreePath[WorktreePath.scala]
    end

    subgraph "Infrastructure Layer (Effects)"
        Tmux[Tmux.scala]
        GitWorktree[GitWorktree.scala]
        Process[Process.scala]
    end

    Commands --> start
    start --> IssueId
    start --> WorktreePath
    start --> Tmux
    start --> GitWorktree
    Tmux --> Process
    GitWorktree --> Process
```

---

## Test Summary

| Test | Type | Verifies |
|------|------|----------|
| `IssueIdTest."accepts valid format IWLE-123"` | Unit | Valid issue ID parsing |
| `IssueIdTest."converts lowercase to uppercase"` | Unit | Case normalization |
| `IssueIdTest."trims whitespace"` | Unit | Input sanitization |
| `IssueIdTest."rejects missing dash"` | Unit | Format validation |
| `IssueIdTest."rejects missing number"` | Unit | Format validation |
| `IssueIdTest."rejects empty string"` | Unit | Empty input handling |
| `IssueIdTest."rejects multiple dashes"` | Unit | Format strictness |
| `IssueIdTest."toBranchName returns value unchanged"` | Unit | Branch name derivation |
| `WorktreePathTest."directoryName combines project and issue"` | Unit | Directory naming |
| `WorktreePathTest."resolve creates sibling path"` | Unit | Path calculation |
| `WorktreePathTest."sessionName matches directoryName"` | Unit | Tmux session naming |
| `TmuxAdapterTest."sessionExists returns false for non-existent"` | Integration | Session detection |
| `TmuxAdapterTest."sessionExists returns true for existing"` | Integration | Session detection |
| `TmuxAdapterTest."createSession creates detached session"` | Integration | Session creation |
| `TmuxAdapterTest."createSession sets working directory"` | Integration | Session workdir |
| `TmuxAdapterTest."killSession removes existing session"` | Integration | Session cleanup |
| `GitWorktreeAdapterTest."worktreeExists returns false"` | Integration | Worktree detection |
| `GitWorktreeAdapterTest."worktreeExists returns true"` | Integration | Worktree detection |
| `GitWorktreeAdapterTest."branchExists returns false"` | Integration | Branch detection |
| `GitWorktreeAdapterTest."branchExists returns true"` | Integration | Branch detection |
| `GitWorktreeAdapterTest."createWorktree creates with new branch"` | Integration | Worktree creation |
| `GitWorktreeAdapterTest."createWorktreeForBranch uses existing"` | Integration | Existing branch handling |
| `start.bats:"creates worktree for valid issue ID"` | E2E | Full workflow success |
| `start.bats:"fails with missing issue ID"` | E2E | Missing argument error |
| `start.bats:"fails with invalid format"` | E2E | Invalid format error |
| `start.bats:"fails when directory exists"` | E2E | Collision handling |
| `start.bats:"suggests using open for existing worktree"` | E2E | Helpful error hint |
| `start.bats:"fails without config"` | E2E | Config validation |
| `start.bats:"uses existing branch if present"` | E2E | Existing branch reuse |
| `start.bats:"converts lowercase to uppercase"` | E2E | Case normalization |

**Test Counts:**
- Unit tests: 19
- Integration tests: 16
- E2E tests: 14
- **Total: 49 tests**

---

## Files Changed

**12 files changed**

| Status | File | Description |
|--------|------|-------------|
| A | `.iw/core/IssueId.scala` | Issue ID value object with validation |
| A | `.iw/core/WorktreePath.scala` | Worktree path calculation |
| A | `.iw/core/Tmux.scala` | Tmux session management adapter |
| A | `.iw/core/GitWorktree.scala` | Git worktree operations adapter |
| M | `.iw/core/Process.scala` | Added `ProcessResult` case class and `run` method |
| M | `.iw/commands/start.scala` | Full implementation of start command |
| A | `.iw/core/test/IssueIdTest.scala` | Unit tests for IssueId |
| A | `.iw/core/test/WorktreePathTest.scala` | Unit tests for WorktreePath |
| A | `.iw/core/test/TmuxAdapterTest.scala` | Integration tests for Tmux |
| A | `.iw/core/test/GitWorktreeAdapterTest.scala` | Integration tests for GitWorktree |
| A | `.iw/test/start.bats` | E2E tests for start command |
| M | `project-management/issues/IWLE-72/phase-04-tasks.md` | Task tracking updates |

<details>
<summary>Full file list</summary>

- `.iw/core/IssueId.scala` (A)
- `.iw/core/WorktreePath.scala` (A)
- `.iw/core/Tmux.scala` (A)
- `.iw/core/GitWorktree.scala` (A)
- `.iw/core/Process.scala` (M)
- `.iw/commands/start.scala` (M)
- `.iw/core/test/IssueIdTest.scala` (A)
- `.iw/core/test/WorktreePathTest.scala` (A)
- `.iw/core/test/TmuxAdapterTest.scala` (A)
- `.iw/core/test/GitWorktreeAdapterTest.scala` (A)
- `.iw/test/start.bats` (A)
- `project-management/issues/IWLE-72/phase-04-tasks.md` (M)

</details>

---

## Key Implementation Notes

1. **Functional Core**: `IssueId` and `WorktreePath` are pure value objects with no side effects
2. **Imperative Shell**: `TmuxAdapter` and `GitWorktreeAdapter` encapsulate all shell interactions
3. **Error Handling**: Uses `Either[String, Unit]` for operations that can fail
4. **Collision Detection**: Checks for existing directory, worktree, and tmux session before creating
5. **Cleanup on Failure**: If tmux session creation fails, the worktree is removed
6. **Case Normalization**: Issue IDs are converted to uppercase for consistency
