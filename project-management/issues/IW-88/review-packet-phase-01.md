---
generated_from: 22d1ae2f1e9ed9a059949cb1f6f823e98d7efd11
generated_at: 2026-01-14T10:31:13+01:00
branch: IW-88-phase-01
issue_id: IW-88
phase: 1
files_analyzed:
  - .iw/core/GitHubClient.scala
  - .iw/core/IssueSearchService.scala
  - .iw/core/CaskServer.scala
  - .iw/core/test/GitHubClientTest.scala
  - .iw/core/test/IssueSearchServiceTest.scala
---

# Phase 1: Recent issues - GitHub

## Goals

This phase implements the ability to fetch and display 5 most recent open issues from GitHub when the Create Worktree modal opens. This establishes the foundation pattern that will be replicated for Linear and YouTrack in later phases.

Key objectives:
- Add GitHub client method to list recent issues via `gh` CLI
- Add application service method to fetch recent issues
- Add `/api/issues/recent` API endpoint returning HTML fragment
- Maintain consistent architecture (Functional Core, Imperative Shell)
- Provide comprehensive test coverage for all new code

## Scenarios

- [ ] GitHubClient.listRecentIssues() fetches 5 recent open issues
- [ ] Issues are sorted by most recently updated (gh CLI default)
- [ ] `/api/issues/recent` endpoint returns HTML fragment with issue list
- [ ] Empty state renders when no issues found
- [ ] Error cases return empty list without crashing the server
- [ ] gh CLI not installed returns appropriate error message
- [ ] gh CLI not authenticated returns appropriate error message
- [ ] All unit tests pass with comprehensive coverage

## Entry Points

Start your review from these locations:

| File | Method/Class | Why Start Here |
|------|--------------|----------------|
| `.iw/core/GitHubClient.scala` | `listRecentIssues()` (line 368) | Infrastructure layer entry point - fetches issues from GitHub via gh CLI |
| `.iw/core/IssueSearchService.scala` | `fetchRecent()` (line 128) | Application service orchestrating recent issue fetching and URL building |
| `.iw/core/CaskServer.scala` | `recentIssues()` endpoint (line 316) | HTTP API endpoint serving recent issues as HTML fragment |

## Architecture Overview

This diagram shows how the new recent issues feature fits within the existing layered architecture.

```mermaid
C4Context
    title System Context - Recent Issues Feature

    Person(user, "Developer", "Uses Create Worktree modal")
    
    System_Boundary(iw_cli, "iw-cli") {
        Container(web, "Web Dashboard", "Cask/HTMX", "Dashboard UI with Create Worktree modal")
        Container(server, "API Server", "CaskServer", "HTTP endpoints for recent issues")
        Container(app, "Application Services", "IssueSearchService", "Business logic layer")
        Container(infra, "Infrastructure", "GitHubClient", "GitHub API integration")
    }
    
    System_Ext(github, "GitHub", "Issue tracking via gh CLI")
    
    Rel(user, web, "Opens Create Worktree modal")
    Rel(web, server, "GET /api/issues/recent", "HTMX")
    Rel(server, app, "fetchRecent()", "calls")
    Rel(app, infra, "listRecentIssues()", "delegates")
    Rel(infra, github, "gh issue list --state open --limit 5", "executes")
    
    UpdateLayoutConfig($c4ShapeInRow="3", $c4BoundaryInRow="1")
```

**Key points for reviewer:**
- New functionality follows existing layered architecture
- No changes to domain layer (reuses existing `Issue` model)
- Clean separation: HTTP -> Application -> Infrastructure -> External API
- All GitHub communication through gh CLI (no direct HTTP calls)

## Component Relationships

This diagram shows the relationships between components modified in this phase.

```mermaid
flowchart TB
    subgraph presentation["Presentation Layer"]
        E1[CaskServer<br/><i>modified</i>]
        E2[SearchResultsView<br/><i>existing</i>]
    end
    
    subgraph application["Application Layer"]
        A1[IssueSearchService<br/><i>modified</i>]
    end
    
    subgraph infrastructure["Infrastructure Layer"]
        I1[GitHubClient<br/><i>modified</i>]
        I2[CommandRunner<br/><i>existing</i>]
    end
    
    subgraph domain["Domain Layer"]
        D1[Issue<br/><i>existing</i>]
        D2[IssueSearchResult<br/><i>existing</i>]
    end
    
    subgraph external["External Systems"]
        X1[(GitHub API<br/>via gh CLI)]
    end
    
    E1 -->|"/api/issues/recent<br/>endpoint"| A1
    E1 -->|renders HTML| E2
    A1 -->|"fetchRecent()"| A1
    A1 -->|"listRecentIssues()"| I1
    I1 -->|executes| I2
    I2 -->|"gh issue list"| X1
    X1 -->|JSON array| I1
    I1 -->|"List[Issue]"| A1
    A1 -->|"List[IssueSearchResult]"| E1
    A1 -.->|uses| D1
    A1 -.->|converts to| D2
    
    classDef modified fill:#ffd700
    classDef new fill:#90EE90
    classDef existing fill:#E0E0E0
    
    class E1,A1,I1 modified
    class E2,I2,D1,D2 existing
```

**Key points for reviewer:**
- No new domain objects needed - reuses `Issue` and `IssueSearchResult`
- `IssueSearchService.fetchRecent()` mirrors existing `search()` pattern
- `GitHubClient.listRecentIssues()` follows same structure as `fetchIssue()`
- All components inject dependencies for testability
- Command execution abstracted through `CommandRunner`

## Key Flow: Fetching Recent Issues

This sequence diagram shows the runtime flow when the modal loads recent issues.

```mermaid
sequenceDiagram
    participant Browser
    participant CaskServer
    participant IssueSearchService
    participant GitHubClient
    participant gh as gh CLI

    Browser->>CaskServer: GET /api/issues/recent
    activate CaskServer
    
    CaskServer->>CaskServer: Load config from<br/>.iw/config.conf
    CaskServer->>CaskServer: Build fetchRecentIssues<br/>function for GitHub
    
    CaskServer->>IssueSearchService: fetchRecent(config, fetchFn)
    activate IssueSearchService
    
    IssueSearchService->>GitHubClient: listRecentIssues(repo, limit=5)
    activate GitHubClient
    
    GitHubClient->>GitHubClient: validateGhPrerequisites()
    
    alt gh not installed or not authenticated
        GitHubClient-->>IssueSearchService: Left(error)
        IssueSearchService-->>CaskServer: Left(error)
        CaskServer->>Browser: HTML with empty results
    else gh is available
        GitHubClient->>gh: gh issue list --state open<br/>--limit 5 --json ...
        gh-->>GitHubClient: JSON array of issues
        GitHubClient->>GitHubClient: parseListRecentIssuesResponse()
        GitHubClient-->>IssueSearchService: Right(List[Issue])
        deactivate GitHubClient
        
        IssueSearchService->>IssueSearchService: Convert to IssueSearchResult<br/>Build URLs<br/>Check worktrees
        IssueSearchService-->>CaskServer: Right(List[IssueSearchResult])
        deactivate IssueSearchService
        
        CaskServer->>CaskServer: SearchResultsView.render()
        CaskServer->>Browser: HTML fragment with issues
    end
    
    deactivate CaskServer
```

**Key points for reviewer:**
- Error handling at every layer - errors converted to empty results
- Prerequisite validation prevents execution if gh CLI unavailable
- JSON parsing happens in GitHubClient (infrastructure layer)
- Domain conversion happens in IssueSearchService (application layer)
- HTML rendering happens in CaskServer (presentation layer)

## Layer Diagram (FCIS Architecture)

This diagram shows how the implementation maps to Functional Core / Imperative Shell architecture.

```mermaid
flowchart TB
    subgraph shell["Imperative Shell (Side Effects)"]
        direction TB
        S1[CaskServer.recentIssues<br/>HTTP I/O]
        S2[GitHubClient.listRecentIssues<br/>Command execution]
        S3[CommandRunner.execute<br/>Process spawning]
        S4[ConfigFileRepository<br/>File I/O]
    end
    
    subgraph core["Functional Core (Pure Logic)"]
        direction TB
        C1[IssueSearchService.fetchRecent<br/>Business logic]
        C2[GitHubClient.buildListRecentIssuesCommand<br/>Command building]
        C3[GitHubClient.parseListRecentIssuesResponse<br/>JSON parsing]
        C4[IssueId.parse<br/>ID validation]
        C5[SearchResultsView.render<br/>HTML generation]
    end
    
    S1 -->|delegates to| C1
    C1 -->|needs issues| S2
    S2 -->|builds args with| C2
    S2 -->|executes via| S3
    S2 -->|parses with| C3
    S1 -->|loads config via| S4
    C1 -.->|validates IDs with| C4
    S1 -->|renders with| C5
    
    classDef effectful fill:#ffcccc
    classDef pure fill:#ccffcc
    
    class S1,S2,S3,S4 effectful
    class C1,C2,C3,C4,C5 pure
```

**Key points for reviewer:**
- Pure functions are heavily tested (easily testable without mocks)
- Effectful functions inject dependencies for testability
- Command building and JSON parsing are pure (no side effects)
- Only command execution and HTTP handling have side effects
- This pattern makes incremental testing straightforward

## Test Summary

| Test | Type | Verifies |
|------|------|----------|
| `GitHubClientTest."buildListRecentIssuesCommand with default limit"` | Unit | Command structure with default limit=5 |
| `GitHubClientTest."buildListRecentIssuesCommand with custom limit"` | Unit | Command structure with custom limit |
| `GitHubClientTest."parseListRecentIssuesResponse parses valid JSON array"` | Unit | JSON parsing with multiple issues |
| `GitHubClientTest."parseListRecentIssuesResponse parses empty array"` | Unit | Empty array returns empty list |
| `GitHubClientTest."parseListRecentIssuesResponse handles malformed JSON"` | Unit | Graceful error handling for bad JSON |
| `GitHubClientTest."parseListRecentIssuesResponse handles missing required fields"` | Unit | Error handling for incomplete data |
| `GitHubClientTest."listRecentIssues success case with mocked command"` | Unit | Full flow with mocked command execution |
| `GitHubClientTest."listRecentIssues fails when gh CLI not available"` | Unit | Prerequisite validation - gh not installed |
| `GitHubClientTest."listRecentIssues fails when gh CLI not authenticated"` | Unit | Prerequisite validation - gh not authenticated |
| `IssueSearchServiceTest."fetchRecent success case with GitHub tracker"` | Unit | Application service happy path |
| `IssueSearchServiceTest."fetchRecent with worktree check integration"` | Unit | Worktree existence flag integration |
| `IssueSearchServiceTest."fetchRecent handles fetch errors gracefully"` | Unit | Error propagation from infrastructure |
| `IssueSearchServiceTest."fetchRecent returns empty list when no issues"` | Unit | Empty state handling |

Coverage: 13 unit tests covering all new methods and error paths. All infrastructure functions inject dependencies (isCommandAvailable, execCommand) for testability without real gh CLI execution.

## Files Changed

**5 files** changed, +421 insertions (source + tests)

<details>
<summary>Full file list</summary>

**Source files (3):**
- `.iw/core/GitHubClient.scala` (+92 lines)
  - Added: `buildListRecentIssuesCommand()`, `parseListRecentIssuesResponse()`, `listRecentIssues()`
- `.iw/core/IssueSearchService.scala` (+36 lines)
  - Added: `fetchRecent()` method
- `.iw/core/CaskServer.scala` (+66 lines)
  - Added: `/api/issues/recent` endpoint, `buildFetchRecentFunction()` helper

**Test files (2):**
- `.iw/core/test/GitHubClientTest.scala` (+133 lines)
  - 9 new tests for recent issues functionality
- `.iw/core/test/IssueSearchServiceTest.scala` (+94 lines)
  - 4 new tests for fetchRecent method

**Documentation files (5):**
- `project-management/issues/IW-88/analysis.md` (+542 lines) - Story-driven analysis
- `project-management/issues/IW-88/phase-01-context.md` (+195 lines) - Phase context
- `project-management/issues/IW-88/phase-01-tasks.md` (+68 lines) - Task breakdown
- `project-management/issues/IW-88/review-state.json` (+9 lines) - Review state metadata
- `project-management/issues/IW-88/tasks.md` (+29 lines) - Task index

</details>

## Notes for Reviewers

**Testing approach:**
- All tests follow TDD approach (tests written first, then implementation)
- Pure functions tested directly without mocking
- Effectful functions tested with injected dependencies
- No actual gh CLI execution in tests (fully mocked)

**Error handling strategy:**
- Prerequisite validation prevents execution if gh CLI unavailable
- JSON parsing errors return Left with descriptive messages
- API errors propagated through Either monad
- Server endpoint converts all errors to empty HTML (never returns 500)

**Consistency with existing patterns:**
- `listRecentIssues()` mirrors structure of `fetchIssue()`
- `fetchRecent()` mirrors structure of `search()`
- `/api/issues/recent` endpoint mirrors `/api/issues/search`
- All methods follow existing error handling patterns

**Future phases:**
- Phase 2: Add title-based search for GitHub
- Phase 3-4: Add recent issues + search for Linear
- Phase 5-6: Add recent issues + search for YouTrack
- Phase 7: Wire up `/api/issues/recent` to modal load event (UI integration)
