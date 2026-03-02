# Technical Analysis: Track main project worktrees independently from issue worktrees

**Issue:** IW-148
**Created:** 2026-03-02
**Status:** Draft

## Problem Statement

Users cannot use the dashboard to bootstrap their first issue worktree for a project. The dashboard only displays projects when at least one issue worktree exists, because projects are derived from issue worktree paths via `MainProjectService.deriveFromWorktrees()`. This creates a chicken-and-egg problem: you need to see the project in the dashboard to create a worktree from it, but you need a worktree for the project to appear.

The immediate user impact is that any project with zero active issue worktrees is invisible in the dashboard. Users must fall back to the CLI (`./iw start <issue-id>`) to create their first worktree, defeating the purpose of the dashboard as a self-service entry point.

## Proposed Solution

### High-Level Approach

Add a `projects` map to `ServerState` that stores `ProjectRegistration` entries independently from issue worktrees. Projects get registered when the first `./iw start` or `./iw register` runs from a project directory, and they persist even when all issue worktrees for that project are removed.

`MainProjectService` gains a second code path: in addition to deriving projects from worktrees (which continues to work for backward compatibility), it also reads from the explicit project registry. The dashboard merges both sources, so projects always appear once registered regardless of worktree count.

The CLI commands `start` and `register` are extended to also register the main project with the server (a one-line addition to each). A dedicated `PUT /api/v1/projects/:projectName` endpoint accepts project registrations.

### Why This Approach

The alternative of filesystem scanning (looking for directories with `.iw/config.conf`) was considered but rejected because it requires knowing which directories to scan, introduces filesystem I/O at dashboard render time, and couples the dashboard to the user's directory layout. Explicit registration is consistent with how issue worktrees already work, keeps the dashboard stateless at render time, and gives users control over which projects appear.

## Architecture Design

**Purpose:** Define WHAT components each layer needs, not HOW they're implemented.

### Domain Layer

**Components:**
- `ProjectRegistration` - value object in `model/` representing a registered main project (path, projectName, trackerType, team, trackerUrl, registeredAt)
- Extension to `ServerState` - add `projects: Map[String, ProjectRegistration]` field
- Extension to `ServerStateCodec.StateJson` - add `projects` field to wire format

**Responsibilities:**
- Define the shape of a project registration independent of issue worktrees
- Enforce invariants: project path must be non-empty, projectName must be non-empty
- Maintain backward compatibility: `ServerState` with missing `projects` field defaults to `Map.empty`
- `ProjectRegistration.create()` validation factory (same pattern as `WorktreeRegistration.create()`)

**Estimated Effort:** 2-3 hours
**Complexity:** Straightforward

---

### Application Layer

**Components:**
- `MainProjectService` extension - add method to merge registered projects with derived-from-worktrees projects
- `ProjectRegistrationService` - pure business logic for registering/deregistering projects (analogous to `WorktreeRegistrationService`)
- `ServerStateService` extension - add `updateProject` method (following `updateWorktree` pattern)

**Responsibilities:**
- `MainProjectService.resolveProjects()`: merge explicitly registered projects with worktree-derived projects, deduplicating by path
- `ProjectRegistrationService.register()`: validate inputs, create/update project registration in state
- `DashboardService.renderDashboard()`: use merged project list instead of worktree-derived only

**Estimated Effort:** 3-4 hours
**Complexity:** Moderate

---

### Infrastructure Layer

**Components:**
- `StateRepository` - already handles full `ServerState` serialization; just needs to include the `projects` field (automatic via codec update)
- `ServerClient` extension - add `registerProject()` method for CLI-to-server communication
- `StateReader` - same automatic pickup via codec update
- `CaskServer` extension - add `PUT /api/v1/projects/:projectName` endpoint

**Responsibilities:**
- Persist project registrations alongside worktree registrations in `state.json`
- Backward-compatible deserialization (missing `projects` field defaults to `Map.empty`)
- HTTP endpoint for project registration
- HTTP endpoint passes through to `ProjectRegistrationService` for validation

**Estimated Effort:** 3-4 hours
**Complexity:** Moderate

---

### Presentation Layer

**Components:**
- `MainProjectsView` - minor update: already handles the project list, just receives a larger list now
- `ProjectDetailsView` - update `renderNotFound`: project exists (registered) but has zero worktrees is no longer a 404
- `DashboardService.renderDashboard()` - use merged project source
- `CaskServer.projectDetails()` - look up project from registered projects when no worktrees exist

**Responsibilities:**
- Display registered projects with zero worktrees (worktree count shows "0 worktrees")
- Project details page works for projects with no worktrees (shows empty state with "Create Worktree" button)
- "Create Worktree" button on zero-worktree project cards uses the registered project path

**Estimated Effort:** 2-3 hours
**Complexity:** Straightforward

---

### CLI Integration Layer

**Components:**
- `start.scala` extension - register main project after creating worktree
- `register.scala` extension - register main project alongside worktree registration
- `projects.scala` extension - also read from registered projects (not just derive from worktrees)

**Responsibilities:**
- Ensure every `./iw start` or `./iw register` invocation registers the main project
- Extract project metadata from `.iw/config.conf` at the call site (already loaded by these commands)
- Best-effort registration (same pattern as current worktree registration: warn on failure, don't error)

**Estimated Effort:** 1-2 hours
**Complexity:** Straightforward

---

## Technical Decisions

### Patterns

- Follow existing `WorktreeRegistration` / `WorktreeRegistrationService` / `ServerStateService.updateWorktree` pattern for projects
- Merge-based project resolution: union of registered projects and worktree-derived projects, deduplicated by path
- Same `create()` validation factory pattern for `ProjectRegistration`

### Technology Choices

- **Frameworks/Libraries**: No new dependencies; upickle (already used) handles serialization
- **Data Storage**: Existing `state.json` file (extended with `projects` field)
- **External Systems**: None new

### Integration Points

- `ServerState.projects` feeds into `MainProjectService` alongside `ServerState.worktrees`
- CLI commands (`start`, `register`) call `ServerClient.registerProject()` after their existing logic
- `CaskServer` routes use merged project list for rendering
- `ProjectSummary.computeSummaries()` works unchanged (it already receives a projects list; it will just receive a larger one)

## Technical Risks & Uncertainties

### CLARIFY: Project Identity and Deduplication

When merging registered projects with worktree-derived projects, we need a stable identity key.

**Questions to answer:**
1. Should the deduplication key be the project path (absolute filesystem path) or the project name?
2. What happens if a project is registered from two different machines with different absolute paths (e.g., `/home/alice/projects/iw-cli` vs `/home/bob/projects/iw-cli`)?
3. Should we allow multiple registrations of the "same" project from different paths?

**Options:**
- **Option A: Deduplicate by absolute path** - Simple, deterministic. Two different paths = two different projects. Matches current worktree behavior. Downside: moving a project directory orphans the registration.
- **Option B: Deduplicate by project name** - More user-friendly. Risk of name collisions if user has two different projects with the same directory name.
- **Option C: Deduplicate by path, with projectName as display key** - Use path as the storage key (like worktrees use issueId), display projectName in the UI. This is what the current worktree-derivation approach effectively does.

**Recommended:** Option C (path as key, name for display). This matches existing behavior.

**Impact:** Determines the key for `ServerState.projects` map and affects merge logic.

---

### CLARIFY: Auto-Registration Timing

**Questions to answer:**
1. Should running `./iw start <issue-id>` from the main project directory automatically register that project, or should there be a separate `./iw project register` command?
2. If auto-registering, should it happen on every `start`/`register` call (idempotent), or only if not already registered?

**Options:**
- **Option A: Auto-register on every `start`/`register`** - Simplest, idempotent, no new commands needed. Slight overhead of an extra HTTP call per `start`.
- **Option B: Separate `./iw project register` command** - Explicit, but adds friction. Users must remember to run it.
- **Option C: Auto-register on first `start`, skip if already registered** - Optimized but requires checking registration status first (another HTTP call or local state check).

**Recommended:** Option A. The overhead of an idempotent PUT is negligible, and it keeps things simple.

**Impact:** Determines whether we need a new CLI command and how `start.scala`/`register.scala` change.

---

### CLARIFY: Pruning Stale Project Registrations

**Questions to answer:**
1. Should the dashboard auto-prune projects whose paths no longer exist on disk?
2. The current worktree auto-prune logic (`stateService.pruneWorktrees`) checks `os.exists(os.Path(wt.path))`. Should we do the same for projects?

**Options:**
- **Option A: Auto-prune on dashboard load** - Consistent with worktree behavior. Removes projects if directory is deleted/moved.
- **Option B: Never auto-prune** - Projects persist until explicitly removed. Useful if directory is temporarily unmounted.
- **Option C: Manual prune only** - Add `./iw project remove` command. No auto-pruning.

**Recommended:** Option A for consistency with existing worktree pruning behavior.

**Impact:** Determines whether we need a prune method and when it runs.

---

## Total Estimates

**Per-Layer Breakdown:**
- Domain Layer: 2-3 hours
- Application Layer: 3-4 hours
- Infrastructure Layer: 3-4 hours
- Presentation Layer: 2-3 hours
- CLI Integration: 1-2 hours

**Total Range:** 11 - 16 hours

**Confidence:** Medium-High

**Reasoning:**
- The pattern is well-established: we're replicating `WorktreeRegistration` infrastructure for projects
- No new external dependencies or complex algorithms
- State serialization backward compatibility is the main technical risk
- The widest variance is in the application layer (merge logic nuances) and infrastructure layer (endpoint + codec testing)

## Testing Strategy

### Per-Layer Testing

**Domain Layer:**
- Unit: `ProjectRegistration.create()` validation (empty path, empty name, valid case)
- Unit: `ServerState` with `projects` field (add, remove, list)
- Unit: `ServerStateCodec` round-trip serialization with projects field
- Unit: Backward-compatible deserialization (state.json without `projects` key)

**Application Layer:**
- Unit: `ProjectRegistrationService.register()` - new project, update existing, validation errors
- Unit: `MainProjectService.resolveProjects()` - merge registered + derived, deduplication
- Unit: `MainProjectService.resolveProjects()` - registered projects with zero worktrees appear
- Unit: `MainProjectService.resolveProjects()` - worktree-derived projects without registration still appear
- Unit: `ProjectSummary.computeSummaries()` - projects with zero worktrees get count 0

**Infrastructure Layer:**
- Integration: `StateRepository` read/write with projects field
- Integration: `StateRepository` read backward-compatible state.json (no projects key)
- Unit: `CaskServer` PUT `/api/v1/projects/:projectName` endpoint (register, update, validation error)
- Unit: `ServerClient.registerProject()` - success and error paths

**Presentation Layer:**
- Unit: `MainProjectsView.render()` - projects with zero worktrees render correctly
- Unit: `ProjectDetailsView` - project exists with zero worktrees shows empty state (not 404)
- Integration: Dashboard renders project cards for registered projects with no worktrees
- E2E: Register project via API, verify it appears on dashboard with zero worktrees

**Test Data Strategy:**
- Extend existing `TestFixtures` with project registration fixtures
- Use in-memory state for unit tests (same pattern as existing tests)
- Temp directories for integration tests (same pattern as `StateRepositoryTest`)

**Regression Coverage:**
- Existing `MainProjectServiceTest` must still pass (worktree-derived projects unchanged)
- Existing `DashboardServiceTest` must still pass
- Existing `CaskServerTest` must still pass
- Existing `ProjectSummaryTest` must still pass
- Existing E2E tests for `start`, `register`, `projects` must still pass

## Deployment Considerations

### Database Changes
- `state.json` gains a `projects` field. Backward compatible: old state files without the field are read as having an empty projects map.
- No migration script needed; the default value handles it.

### Configuration Changes
- None. No new environment variables or config files.

### Rollout Strategy
- Deploy server with updated `state.json` support. Old state files work unchanged.
- Deploy updated CLI commands. Projects start appearing in dashboard as users run `./iw start` or `./iw register`.
- No flag-gating needed; the feature is additive.

### Rollback Plan
- Revert to previous server version. The `projects` field in `state.json` is ignored by older versions (upickle ignores unknown fields by default). No data loss.

## Dependencies

### Prerequisites
- None. All required infrastructure exists.

### Layer Dependencies
- Domain Layer has no dependencies (pure types)
- Application Layer depends on Domain Layer (uses `ProjectRegistration`, extended `ServerState`)
- Infrastructure Layer depends on Domain + Application (serializes `ProjectRegistration`, calls `ProjectRegistrationService`)
- Presentation Layer depends on Application (uses merged project list)
- CLI Integration depends on Infrastructure (calls `ServerClient.registerProject()`)

### External Blockers
- None.

## Risks & Mitigations

### Risk 1: State file backward compatibility
**Likelihood:** Low
**Impact:** High
**Mitigation:** upickle's `macroRW` with default values handles missing fields. Add explicit backward-compat test reading a state.json without the `projects` key. The `StateJson` case class already uses default values for optional maps.

### Risk 2: Merge logic producing duplicates
**Likelihood:** Medium
**Impact:** Low (cosmetic: duplicate project cards)
**Mitigation:** Deduplicate by path in `resolveProjects()`. Unit test with overlapping registered and derived projects.

### Risk 3: Race condition between worktree creation and project registration
**Likelihood:** Low
**Impact:** Low (project just appears slightly later)
**Mitigation:** Both are best-effort. Project will appear on next dashboard refresh regardless. No transactional guarantee needed.

---

## Implementation Sequence

**Recommended Layer Order:**

1. **Domain Layer** - Pure types with no dependencies. Foundation for all other layers. `ProjectRegistration`, `ServerState` extension, codec updates.
2. **Application Layer** - Business logic that uses domain types. `ProjectRegistrationService`, `MainProjectService` merge logic, `ServerStateService` extension.
3. **Infrastructure Layer** - Persistence and HTTP endpoints. `StateRepository` automatic pickup, `CaskServer` endpoint, `ServerClient.registerProject()`.
4. **Presentation Layer** - View updates. `MainProjectsView` handles zero-worktree projects, `ProjectDetailsView` handles registered-but-empty projects.
5. **CLI Integration** - Wire up registration calls in `start.scala`, `register.scala`, `projects.scala`.

**Ordering Rationale:**
- Domain first because all other layers import from it
- Application before infrastructure because the HTTP endpoint delegates to `ProjectRegistrationService`
- Presentation after application because views consume the merged project list
- CLI integration last because it depends on `ServerClient.registerProject()` (infrastructure)
- Domain and parts of application could be parallelized, but the layers are small enough that sequential is fine

## Documentation Requirements

- [ ] Code documentation (inline comments for complex logic)
- [ ] API documentation (new `PUT /api/v1/projects/:projectName` endpoint)
- [ ] Architecture decision record (not needed; follows established pattern)
- [ ] User-facing documentation (not needed; behavior is transparent)
- [ ] Migration guide (not needed; backward compatible)

---

**Analysis Status:** Ready for Review

**Next Steps:**
1. Resolve CLARIFY markers with stakeholders
2. Run **wf-create-tasks** with issue IW-148
3. Run **wf-implement** for layer-by-layer implementation
