# Phase 5 Context: Main Projects Listing

**Issue:** IW-79
**Phase:** 5 - Main Projects Listing
**Estimated:** 3-4 hours

## Goals

This phase addresses the UX gap identified during human review: the dashboard manages worktrees from multiple projects, but the current "Create Worktree" button only uses the server's CWD config. We will:

1. Derive "main projects" from registered worktree paths
2. Show a main projects section above the worktree list with per-project create buttons
3. Scope the modal and search to the selected project
4. Remove the global "Create Worktree" button from the header

## Scope

### In Scope
- `MainProject` domain model (path, projectName, config)
- Path derivation logic: `{mainProjectPath}-{issueId}` → `{mainProjectPath}`
- Config loading from arbitrary project paths (not just CWD)
- Main projects section in dashboard with create buttons
- Project-scoped modal (`?project=/path/to/project`)
- Project-scoped search API (`?project=/path/to/project`)
- Empty state when no worktrees registered
- Graceful handling of missing/invalid main projects

### Out of Scope
- Caching project configs (load on-demand for simplicity)
- Manual project registration (derive from worktrees only)
- Project-level settings/preferences
- Worktree grouping by project in the worktree list (just show flat list)

## Dependencies

**From Previous Phases:**
- `CreateWorktreeModal.render()` - needs modification to accept project context
- `IssueSearchService.search()` - needs modification to use project-specific config
- `CaskServer` endpoints - need project parameter handling
- `DashboardService` - has the "Create Worktree" button to remove, needs main projects section

**External Dependencies:**
- Worktree registration data (from existing registry)
- `.iw/config.conf` files in main project directories
- Filesystem for existence checks

## Technical Approach

### 1. Domain Layer

**MainProject value object:**
```scala
case class MainProject(
  path: os.Path,           // e.g., /home/user/projects/iw-cli
  projectName: String,     // e.g., "iw-cli"
  trackerType: String,     // e.g., "github"
  team: String             // e.g., "iterative-works/iw-cli"
)
```

**Path derivation logic:**
- Worktree path pattern: `{mainProjectPath}-{issueId}` (e.g., `/projects/iw-cli-IW-79`)
- Extract main project path by stripping `-{issueId}` suffix
- IssueId pattern: `-[A-Z]+-[0-9]+$` or similar

### 2. Application Layer

**MainProjectService:**
```scala
object MainProjectService:
  def deriveFromWorktrees(worktrees: List[Worktree]): List[MainProject]
  def loadConfig(mainProjectPath: os.Path): Either[String, ProjectConfig]
```

**Modified IssueSearchService:**
- Accept project path parameter
- Load config from specified path instead of CWD

### 3. Infrastructure Layer

**Config loading from arbitrary paths:**
- Read `.iw/config.conf` from specified path
- Return error if path doesn't exist or config is missing

### 4. Presentation Layer

**MainProjectsView:**
```scala
object MainProjectsView:
  def render(projects: List[MainProject]): Frag
  // Shows card for each project with name, tracker info, and [+ Create] button
```

**Modified endpoints:**
- `GET /api/modal/create-worktree?project=/path/to/project`
- `GET /api/issues/search?q=...&project=/path/to/project`
- POST endpoint already receives project context from modal

**Dashboard changes:**
- Remove "Create Worktree" button from header
- Add main projects section above worktree list
- Pass project path to modal when opening

## Files to Modify

**New files:**
- `.iw/core/domain/MainProject.scala` - MainProject case class
- `.iw/core/application/MainProjectService.scala` - Derivation and config loading
- `.iw/core/presentation/views/MainProjectsView.scala` - Main projects section rendering
- `.iw/core/test/MainProjectTest.scala` - Domain tests
- `.iw/core/test/MainProjectServiceTest.scala` - Service tests
- `.iw/core/test/MainProjectsViewTest.scala` - View tests

**Modified files:**
- `.iw/core/CaskServer.scala` - Add project parameter to endpoints
- `.iw/core/DashboardService.scala` - Remove header button, add main projects section
- `.iw/core/presentation/views/CreateWorktreeModal.scala` - Accept project context
- `.iw/core/IssueSearchService.scala` - Accept project path for config
- `.iw/core/test/CreateWorktreeModalTest.scala` - Update for project context
- `.iw/core/test/IssueSearchServiceTest.scala` - Update for project path

## Testing Strategy

### Unit Tests
1. **MainProject domain tests:**
   - Construction with required fields
   - Field access
   - Equality

2. **MainProjectService tests:**
   - Path derivation from worktree paths
   - Deduplication of main projects
   - Config loading success/failure
   - Handling of missing directories
   - Various issue ID formats

3. **MainProjectsView tests:**
   - Rendering with multiple projects
   - Empty state rendering
   - Create button with correct project path
   - Tracker info display

### Integration Tests
- Config loading from real filesystem paths
- Project-scoped search endpoint

### Manual Testing
- Dashboard shows main projects derived from worktrees
- Create button opens modal scoped to correct project
- Search uses correct tracker API for each project

## Acceptance Criteria

1. ✅ Main projects section visible above worktree list
2. ✅ Projects derived from registered worktrees (not manually registered)
3. ✅ Each project shows tracker type and identifier
4. ✅ Create button opens modal scoped to that project
5. ✅ Search uses correct tracker API for selected project
6. ✅ Missing/invalid main projects handled gracefully (not shown)
7. ✅ Empty state shown when no worktrees registered
8. ✅ Multiple worktrees from same project = one entry in main projects
9. ✅ Global "Create Worktree" button removed from header

## Notes

- Use `os-lib` for filesystem operations (already in project)
- Config parsing can reuse existing HOCON parsing if available
- Project path in URL should be URL-encoded
- Consider extracting project name from path (last component after stripping issue ID suffix)
