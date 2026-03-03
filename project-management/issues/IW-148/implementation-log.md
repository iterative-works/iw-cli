# Implementation Log: Track main project worktrees independently from issue worktrees

Issue: IW-148

This log tracks the evolution of implementation across phases.

---

## Phase 1: Domain Layer (2026-03-02)

**Layer:** Domain

**What was built:**
- `.iw/core/model/ProjectRegistration.scala` - Value object for registered projects with `create()` validation factory
- `.iw/core/model/ServerState.scala` - Extended with `projects` field, `listProjects`, `removeProject`
- `.iw/core/model/ServerStateCodec.scala` - Added `ReadWriter[ProjectRegistration]`, extended `StateJson` with `projects` field
- `.iw/core/dashboard/StateRepository.scala` - Threading `projects` through read/write
- `.iw/core/dashboard/ServerStateService.scala` - Threading `projects` through empty state constructors

**Dependencies on other layers:**
- None (this is the foundation layer)

**Testing:**
- Unit tests: 18 tests added (8 ProjectRegistration, 5 ServerState, 5 ServerStateCodec)
- Integration tests: 0 (domain layer is pure)

**Code review:**
- Iterations: 1
- Review file: review-phase-01-20260302.md
- No critical issues. Minor style fixes applied (fully-qualified references, duplicate test merged).

**Files changed:**
```
A	.iw/core/model/ProjectRegistration.scala
A	.iw/core/test/ProjectRegistrationTest.scala
M	.iw/core/model/ServerState.scala
M	.iw/core/model/ServerStateCodec.scala
M	.iw/core/dashboard/StateRepository.scala
M	.iw/core/dashboard/ServerStateService.scala
M	.iw/core/test/ServerStateTest.scala
M	.iw/core/test/ServerStateCodecTest.scala
M	.iw/core/test/TestFixtures.scala
```

---

## Phase 2: Application Layer (2026-03-03)

**Layer:** Application

**What was built:**
- `.iw/core/dashboard/ProjectRegistrationService.scala` - Pure business logic for registering/deregistering projects
- `.iw/core/dashboard/application/MainProjectService.scala` - Added `resolveProjects()` to merge registered + derived projects
- `.iw/core/dashboard/ServerStateService.scala` - Added `updateProject()` and `pruneProjects()` methods
- `.iw/core/dashboard/DashboardService.scala` - Updated `renderDashboard()` to accept registered projects
- `.iw/core/dashboard/CaskServer.scala` - Updated to pass `state.projects` to `renderDashboard()`

**Dependencies on other layers:**
- Phase 1 (Domain Layer): `ProjectRegistration`, `ServerState.projects`

**Testing:**
- Unit tests: 19 tests added (8 ProjectRegistrationService, 5 resolveProjects, 4 ServerStateService, 2 DashboardService)
- Integration tests: 0

**Code review:**
- Iterations: 1
- No critical issues.

**Files changed:**
```
A	.iw/core/dashboard/ProjectRegistrationService.scala
A	.iw/core/test/ProjectRegistrationServiceTest.scala
M	.iw/core/dashboard/application/MainProjectService.scala
M	.iw/core/dashboard/ServerStateService.scala
M	.iw/core/dashboard/DashboardService.scala
M	.iw/core/dashboard/CaskServer.scala
M	.iw/core/test/MainProjectServiceTest.scala
M	.iw/core/test/ServerStateServiceTest.scala
M	.iw/core/test/DashboardServiceTest.scala
```

---

## Phase 3: Infrastructure Layer (2026-03-03)

**Layer:** Infrastructure

**What was built:**
- `.iw/core/dashboard/CaskServer.scala` - Added `PUT /api/v1/projects/:projectName` endpoint, auto-pruning of stale projects on dashboard load, updated `projectDetails()` to find registered projects with zero worktrees
- `.iw/core/adapters/ServerClient.scala` - Added `registerProject()` method for CLI-to-server communication

**Dependencies on other layers:**
- Phase 1 (Domain Layer): `ProjectRegistration`, `ServerState.projects`
- Phase 2 (Application Layer): `ProjectRegistrationService.register()`, `ServerStateService.updateProject()`, `pruneProjects()`

**Testing:**
- Integration tests: 8 tests added (5 endpoint, 1 project details, 1 auto-prune, 1 ServerClient disabled)
- Total: 1665 tests passing

**Code review:**
- Iterations: 1
- Review file: review-phase-03-20260303.md
- No critical issues. Warnings about pre-existing code duplication patterns.

**Files changed:**
```
M	.iw/core/adapters/ServerClient.scala
M	.iw/core/dashboard/CaskServer.scala
M	.iw/core/project.scala
M	.iw/core/test/CaskServerTest.scala
M	.iw/core/test/ServerClientTest.scala
```

---
