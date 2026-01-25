# Phase 1: Establish Public API Boundary

**Issue:** IW-126
**Phase:** 1 of 3
**Story:** Developer understands which modules are public API

## Goals

Refactor the `.iw/core/` directory to separate public API modules (intended for script composition) from internal implementation details (dashboard services, caches, views). This creates a self-documenting structure where directory location declares API stability intent.

## Scope

### In Scope
- Analyze current module usage in `.iw/commands/` to identify public API candidates
- Create `api/` subdirectory in `.iw/core/` for public modules
- Create `internal/` subdirectory for implementation details
- Move modules to appropriate locations based on usage analysis
- Update all imports in `.iw/commands/` files
- Update `project.scala` if needed for package organization
- Ensure all tests continue to pass

### Out of Scope
- Documentation (Phase 2)
- Skill updates (Phase 3)
- Any API changes to the modules themselves
- Creating new modules or removing functionality

## Dependencies

### From Previous Phases
None (this is Phase 1)

### External
- Understanding of Scala package/import conventions
- scala-cli module organization patterns

## Technical Approach

### 1. Analysis: Identify Public vs Internal

**Public API candidates** (used by `.iw/commands/` or skill examples):
- `Output.scala` - Console output formatting (heavily used)
- `Git.scala` - Git operations via GitAdapter
- `GitWorktree.scala` - Worktree management
- `IssueId.scala` - Issue ID parsing/validation
- `Issue.scala` - Issue domain type
- `Config.scala` - Configuration types
- `ConfigRepository.scala` - Config loading
- `Process.scala` - Shell execution
- `Prompt.scala` - User prompts
- `Constants.scala` - Shared constants
- `WorktreePath.scala` - Path handling

**Internal modules** (dashboard, caching, services):
- `CaskServer.scala` - HTTP server implementation
- `DashboardService.scala` - Dashboard business logic
- `*CacheService.scala` - All caching services
- `*View.scala` - Presentation components
- `Server*.scala` - Server-related modules
- `Worktree*Service.scala` - Internal worktree services

**Infrastructure adapters** (keep in infrastructure/):
- `GitHubClient.scala`
- `LinearClient.scala`
- `GitLabClient.scala`
- `YouTrackClient.scala`

### 2. Directory Structure After Refactoring

```
.iw/core/
├── api/                    # Public API - stable, documented
│   ├── Output.scala
│   ├── Git.scala
│   ├── GitWorktree.scala
│   ├── IssueId.scala
│   ├── Issue.scala
│   ├── Config.scala
│   ├── ConfigRepository.scala
│   ├── Process.scala
│   ├── Prompt.scala
│   ├── Constants.scala
│   └── WorktreePath.scala
│
├── internal/               # Internal - no stability guarantees
│   ├── CaskServer.scala
│   ├── DashboardService.scala
│   ├── CachedProgress.scala
│   ├── CachedIssue.scala
│   ├── CachedPR.scala
│   ├── CachedReviewState.scala
│   ├── ... (remaining internal modules)
│
├── infrastructure/         # Adapters - selectively public
│   ├── GitHubClient.scala
│   ├── LinearClient.scala
│   ├── GitLabClient.scala
│   ├── YouTrackClient.scala
│   └── CreationLockRegistry.scala
│
├── domain/                 # Keep as-is
├── application/            # Keep as-is
├── presentation/           # Keep as-is (views are internal)
└── project.scala           # Update package declarations
```

### 3. Import Update Strategy

Commands currently use:
- `import iw.core.*` - wildcard import
- `import iw.core.Output` - specific imports
- `import iw.core.infrastructure.*` - infrastructure imports

After refactoring, update to:
- `import iw.core.api.*` - for public API
- `import iw.core.internal.*` - for internal (if needed)
- Infrastructure imports stay the same

### 4. Package Declaration Updates

Each moved file needs its package updated:
- Public API: `package iw.core.api`
- Internal: `package iw.core.internal`

The `project.scala` file may need updates to configure scala-cli to recognize the new structure.

## Files to Modify

### Create Directories
- `.iw/core/api/` (new)
- `.iw/core/internal/` (new)

### Move to api/
- `Output.scala`
- `Git.scala`
- `GitWorktree.scala`
- `IssueId.scala`
- `Issue.scala`
- `Config.scala`
- `ConfigRepository.scala`
- `Process.scala`
- `Prompt.scala`
- `Constants.scala`
- `WorktreePath.scala`

### Move to internal/
All remaining modules in `.iw/core/` root (except `project.scala`)

### Update Imports
All 18 files in `.iw/commands/`:
- `claude-sync.scala`
- `dashboard.scala`
- `doctor.scala`
- `feedback.scala`
- `github.hook-doctor.scala`
- `init.scala`
- `issue.hook-doctor.scala`
- `issue.scala`
- `legacy-branches.hook-doctor.scala`
- `open.scala`
- (and 8 more)

### Possibly Update
- `.iw/core/project.scala` - Package organization

## Testing Strategy

### Compilation Test
After each batch of moves, verify:
```bash
scala-cli compile .iw/core/
```

### Integration Test
After all moves complete:
```bash
./iw test
```

### Manual Verification
- Run a few commands to verify they still work:
  - `./iw issue IW-126`
  - `./iw doctor`
  - `./iw dashboard` (if applicable)

## Acceptance Criteria

1. **Structure**: `.iw/core/api/` contains public modules, `.iw/core/internal/` contains internal modules
2. **Compilation**: All code compiles without errors
3. **Tests**: `./iw test` passes completely
4. **Commands**: All 18 commands work correctly
5. **Imports**: Commands use `iw.core.api.*` for public API
6. **Clean separation**: No public API module imports from internal (allowed: internal imports from api)

## Risks and Mitigations

### Risk: Circular dependencies after split
- **Mitigation**: Analyze dependencies before moving; some modules may need to stay together

### Risk: Tests fail due to import changes
- **Mitigation**: Update test imports along with production code imports

### Risk: Package organization breaks scala-cli
- **Mitigation**: Small incremental moves with compile checks after each

## Notes

- Keep module filenames identical - only package paths change
- Maintain backward compatibility within this PR (can break in future release)
- Document import migration in PR description for users updating their scripts
