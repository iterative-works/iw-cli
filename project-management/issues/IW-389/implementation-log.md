# Implementation Log: Support Forgejo issue tracker

Issue: IW-389

This log tracks the evolution of implementation across phases.

---

## Phase 1: Domain, config & serialization (2026-06-24)

**Layer:** Domain & Configuration (pure functional core, no I/O)

**What was built:**
- `core/model/Constants.scala` — added `TrackerTypeValues.Forgejo = "forgejo"`.
- `core/model/Config.scala` — added `Forgejo` to `enum IssueTrackerType`; added the
  `Forgejo`/`forgejo` arms to `ConfigSerializer.toHocon` and `fromHocon`; added Forgejo
  to the `teamIdentifier` repository-identified arm (alongside GitHub/GitLab).
- `core/model/RepoUrlBuilder.scala` — Forgejo arm builds `baseUrl/repo` via `for/yield`,
  propagating `None` when `repository` or `trackerBaseUrl` is absent.
- `core/model/TrackerUrlBuilder.scala` — Forgejo arm builds `baseUrl/repo/issues`.
- `core/model/CIChecks.scala` — Forgejo grouped with the "check for GitHub/GitLab CI file"
  arm (placeholder default; a dedicated Forgejo Actions arm is deferred to Phase 4 doctor).
- `core/commands/Init.scala`, `core/commands/Issue.scala` — minimal stub arms keeping the
  `-Werror` build green; full Forgejo init/dispatch is Phase 3/4 work.

**Decisions:**
- **Option A** for required fields: `repository`/`baseUrl` stay optional at parse time
  (matching GitHub/GitLab); required-field enforcement deferred to the command/adapter
  layer (Phase 2/3). No tracker enforces presence at parse time today.

**Dependencies on other layers:** none — Layer 1 is the base of the dependency graph.

**Testing:**
- Unit tests added in `core/test/ConfigTest.scala` (Forgejo toHocon/fromHocon/round-trip
  with HOCON idempotence, repository format + path-traversal rejection, baseUrl scheme
  rejection, optional-field behaviour, `team =` absence, teamIdentifier),
  `core/test/ConstantsTest.scala` (forgejo constant), and
  `core/test/RepoUrlBuilderTest.scala` (Forgejo URL with/without baseUrl, missing repo).
- Full `core.test` suite green: 186/186. Compile clean with `-Werror`.

**Code review:**
- Iterations: 1 (4 reviewers: scala3, architecture, testing, style).
- Findings: 2 critical (teamIdentifier omitted Forgejo; RepoUrlBuilder relative-URL on
  missing baseUrl) — both fixed with tests. Naming-convention + coverage warnings fixed.
  CIChecks dedicated arm and `askForTracker` menu deferred to later phases by scope.
- Review file: review-phase-01-20260624-131500.md

**For next phases:**
- Phase 2: `ForgejoClient` HTTP adapter (sttp) — issue read + create.
- Phase 3: `TrackerOps` wiring + `Issue.scala` dispatch + `FORGEJO_API_TOKEN` resolution
  (replaces the stub `Left(...)` arms).
- Phase 4: `Init` menu + `Doctor`/`CIChecks` Forgejo Actions detection + BATS smoke.

**Files changed:**
```
M	core/commands/Init.scala
M	core/commands/Issue.scala
M	core/model/CIChecks.scala
M	core/model/Config.scala
M	core/model/Constants.scala
M	core/model/RepoUrlBuilder.scala
M	core/model/TrackerUrlBuilder.scala
M	core/test/ConfigTest.scala
M	core/test/ConstantsTest.scala
M	core/test/RepoUrlBuilderTest.scala
```

---
