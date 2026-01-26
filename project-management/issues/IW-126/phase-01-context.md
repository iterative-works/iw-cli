# Phase 1: Establish Public API Boundary

**Issue:** IW-126
**Phase:** 1 of 3
**Story:** Developer understands which modules are public API

## Goals

Reorganize `.iw/core/` by architectural responsibility rather than visibility. This creates a structure where:
- Directory location indicates what kind of code it is (pure types, I/O adapters, presentation)
- Public API for scripts = everything except `dashboard/`
- The structure is self-documenting via `CLAUDE.md`

## Architectural Approach

Based on FCIS (Functional Core Imperative Shell) principles, adapted for iw-cli's simpler needs:

```
.iw/core/
├── model/        # Pure domain types - no I/O, no side effects
├── adapters/     # I/O operations - shell commands, API clients
├── output/       # CLI presentation - console formatting
└── dashboard/    # Dashboard server internals - not for scripts
```

**Why this structure:**
- `model/` = functional core (pure, testable without mocks)
- `adapters/` = imperative shell (I/O boundary)
- `output/` = presentation (CLI-specific formatting)
- `dashboard/` = internal implementation (server, caches, views)

**Public API** = `model/` + `adapters/` + `output/` (documented in llms.txt)
**Internal** = `dashboard/` (not documented)

## Scope

### In Scope
- Create new directory structure: `model/`, `adapters/`, `output/`, `dashboard/`
- Move modules to appropriate locations based on responsibility
- Update all imports in `.iw/commands/` and `.iw/core/` files
- Create `CLAUDE.md` documenting placement criteria
- Update `project.scala` for package organization
- Ensure all tests pass

### Out of Scope
- Documentation in llms.txt (Phase 2)
- Skill updates (Phase 3)
- API changes to modules themselves
- Creating new modules or removing functionality

## Dependencies

### From Previous Phases
None (this is Phase 1)

### External
- Scala package/import conventions
- scala-cli module organization patterns

## Technical Approach

### 1. Module Classification

**model/** - Pure domain types (no I/O):
- `IssueId.scala` - issue ID parsing/validation
- `Issue.scala` - issue domain type
- `IssueData.scala` - issue data structures
- `Config.scala` - configuration types
- `ConfigRepository.scala` - config loading (interface)
- `Constants.scala` - shared constants
- `WorktreePath.scala` - path value object
- `WorktreePriority.scala` - priority enum
- `WorktreeRegistration.scala` - registration data
- `ApiToken.scala` - token value object
- `GitStatus.scala` - status data type
- `ReviewState.scala` - review state data
- `PhaseInfo.scala` - phase data
- `WorkflowProgress.scala` - progress data
- `PullRequestData.scala` - PR data
- `ServerConfig.scala` - server config types
- `ServerStatus.scala` - status enum
- `ServerState.scala` - state data
- `CacheConfig.scala` - cache config types
- `DeletionSafety.scala` - safety enum/rules

**adapters/** - I/O operations (shell, network, filesystem):
- `Git.scala` - git operations via shell
- `GitWorktree.scala` - worktree operations via shell
- `Process.scala` - shell command execution
- `Prompt.scala` - user input prompts
- `Tmux.scala` - tmux operations
- `GitHubClient.scala` - GitHub API client
- `LinearClient.scala` - Linear API client
- `GitLabClient.scala` - GitLab API client
- `YouTrackClient.scala` - YouTrack API client
- `CommandRunner.scala` - command execution
- `Log.scala` - logging operations

**output/** - CLI presentation:
- `Output.scala` - console output formatting
- `IssueFormatter.scala` - issue display formatting
- `MarkdownRenderer.scala` - markdown rendering
- `TimestampFormatter.scala` - timestamp display

**dashboard/** - Server internals (not for scripts):
- `CaskServer.scala` - HTTP server
- `DashboardService.scala` - dashboard logic
- `IssueCacheService.scala` - issue caching
- `PullRequestCacheService.scala` - PR caching
- `CachedIssue.scala`, `CachedPR.scala`, `CachedProgress.scala`, `CachedReviewState.scala`
- `ServerClient.scala` - server communication
- `ServerConfigRepository.scala` - server config persistence
- `ServerLifecycleService.scala` - server lifecycle
- `ServerStateService.scala` - server state management
- `StateRepository.scala` - state persistence
- `ReviewStateService.scala` - review state management
- `WorkflowProgressService.scala` - progress tracking
- `WorktreeCardService.scala` - card rendering service
- `WorktreeListSync.scala` - list synchronization
- `WorktreeListView.scala` - list view
- `WorktreeRegistrationService.scala` - registration service
- `WorktreeUnregistrationService.scala` - unregistration service
- `GitStatusService.scala` - git status service
- `GitHubHookDoctor.scala` - hook diagnostics
- `DoctorChecks.scala` - diagnostic checks
- `ArtifactService.scala` - artifact handling
- `FeedbackParser.scala` - feedback parsing
- `MarkdownTaskParser.scala` - task parsing
- `IssueSearchService.scala`, `IssueSearchResult.scala` - search
- `PathValidator.scala` - path validation service
- `RefreshThrottle.scala` - throttling
- `ProcessManager.scala` - process management
- Existing subdirectories: `application/`, `domain/`, `infrastructure/`, `presentation/`

### 2. Directory Structure After Refactoring

```
.iw/core/
├── model/                  # Pure domain types
│   ├── IssueId.scala
│   ├── Issue.scala
│   ├── Config.scala
│   ├── WorktreePath.scala
│   └── ... (other pure types)
│
├── adapters/               # I/O operations
│   ├── Git.scala
│   ├── GitWorktree.scala
│   ├── Process.scala
│   ├── Prompt.scala
│   ├── GitHubClient.scala
│   └── ... (other adapters)
│
├── output/                 # CLI presentation
│   ├── Output.scala
│   ├── IssueFormatter.scala
│   └── ... (other formatters)
│
├── dashboard/              # Server internals
│   ├── CaskServer.scala
│   ├── DashboardService.scala
│   ├── services/           # Moved from root
│   ├── cache/              # Cache-related
│   └── ... (all internal stuff)
│
├── CLAUDE.md         # Documents this structure
├── project.scala
└── test/
```

### 3. Import Update Strategy

**Current imports:**
```scala
import iw.core.*
import iw.core.Output
import iw.core.infrastructure.{GitHubHookDoctor => CoreGitHubHookDoctor}
```

**After refactoring:**
```scala
import iw.core.model.*
import iw.core.adapters.*
import iw.core.output.*
// Dashboard internals (only used within dashboard):
import iw.core.dashboard.*
```

### 4. CLAUDE.md Content

Create `.iw/core/CLAUDE.md` with:
- Directory structure explanation
- Placement decision criteria (flowchart)
- Public API scope definition
- llms.txt maintenance rules
- Import conventions
- Dependency rules

## Files to Modify

### Create Directories
- `.iw/core/model/`
- `.iw/core/adapters/`
- `.iw/core/output/`
- `.iw/core/dashboard/`

### Create Files
- `.iw/core/CLAUDE.md`

### Move Files
See classification in Section 1 above.

### Update Imports
- All 18 files in `.iw/commands/`
- All files in `.iw/core/` that cross-reference moved modules
- Test files in `.iw/core/test/`

### Update Package Declarations
Each moved file needs package updated:
- `package iw.core.model`
- `package iw.core.adapters`
- `package iw.core.output`
- `package iw.core.dashboard`

## Testing Strategy

### Compilation Test
After each batch of moves:
```bash
scala-cli compile .iw/core/
```

### Integration Test
After all moves complete:
```bash
./iw test
```

### Manual Verification
- `./iw issue IW-126` - uses model + adapters + output
- `./iw doctor` - uses adapters
- `./iw worktree list` - uses model + adapters
- `./iw dashboard` - uses dashboard internals

## Acceptance Criteria

1. **Structure**: Four directories exist with modules placed by responsibility
2. **CLAUDE.md**: Documents structure and placement criteria
3. **Compilation**: All code compiles without errors
4. **Tests**: `./iw test` passes completely
5. **Commands**: All 18 commands work correctly
6. **Imports**: Commands use `iw.core.model.*`, `iw.core.adapters.*`, `iw.core.output.*`
7. **Dependencies**:
   - `model/` has no dependencies on other core directories
   - `adapters/` may depend on `model/`
   - `output/` may depend on `model/`
   - `dashboard/` may depend on all others

## Risks and Mitigations

### Risk: Circular dependencies after split
- **Mitigation**: `model/` must remain pure with no dependencies; analyze before moving

### Risk: Some modules don't fit cleanly
- **Mitigation**: When unclear, prefer `adapters/` for anything with I/O, `model/` for pure data

### Risk: Tests fail due to import changes
- **Mitigation**: Update test imports along with production code; batch by directory

### Risk: Package organization breaks scala-cli
- **Mitigation**: Small incremental moves with compile checks after each

## Notes

- Keep module filenames identical - only package paths change
- Existing `application/`, `domain/`, `infrastructure/`, `presentation/` subdirectories move into `dashboard/`
- Document import migration in PR description for users updating their scripts
