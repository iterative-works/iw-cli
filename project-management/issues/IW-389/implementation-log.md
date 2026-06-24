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

## Phase 2: Forgejo HTTP adapter — issue read + create (2026-06-24)

**Layer:** Infrastructure / I/O adapter (imperative shell over the pure domain)

**What was built:**
- `core/adapters/ForgejoClient.scala` — new HTTP adapter for the Forgejo (Gitea-compatible)
  REST API, mirroring `LinearClient`'s injectable-`SyncBackend` seam so every path is
  unit-testable with no network. Public surface: `fetchIssue`, `createIssue`,
  `validateToken`, plus pure helpers (`buildIssueUrl`, `buildCreateIssueUrl`,
  `buildValidateTokenUrl`, `buildCreateIssueBody`, `parseFetchIssueResponse`,
  `parseCreateIssueResponse`). Returns `Either[String, Issue]` / `Either[String,
  CreatedIssue]`; `validateToken` returns `Boolean` (matching `LinearClient`).
- `core/test/ForgejoClientTest.scala` — 33 munit tests using `SyncBackendStub` (network-free):
  URL builders + trailing-slash handling, ID normalization (`PROJ-123` → `…/issues/123`),
  fetch happy path, null/absent assignee, empty/null/absent body, missing required fields
  (`title`/`state` → `Left`), 401/404/500/malformed-JSON, create happy path + 401/404/422,
  `validateToken` 200/401/500, `buildCreateIssueBody` exact-key assertion.

**Decisions:**
- **`Authorization: token <token>`** header; auth via the env-resolved token in Phase 3.
- **ID normalization** via `split("-").last` for the URL index, preserving the full ID in
  `Issue.id` (mirrors GitHub/GitLab).
- **`state` kept verbatim** (`open`/`closed`) — Forgejo emits these directly, unlike
  GitLab's `opened`; no remap needed.
- **`createIssue` success** matches `201 Created` (accepts `200 Ok` defensively).
- **JSON** parsed with `ujson` value-tree navigation (same as all sibling adapters); no
  derived codecs / new library.
- **`CreatedIssue` reused** from the adapters package (defined in `LinearClient.scala`),
  not redefined.

**Deferred (pre-existing family-wide conventions, flagged by review — own ticket, not a
single-adapter divergence):** relocate `CreatedIssue` out of `LinearClient.scala`;
`validateToken` → `Either[String, Boolean]`; narrow the broad `catch Exception`; typed
error ADT; generic network-error text / repository path sanitization. Each matches the
existing `LinearClient`/`YouTrackClient` shape this adapter was built to mirror.

**Dependencies on other layers:**
- Domain: `Issue`, `IssueId`, `ApiToken` (`core/model`), `CreatedIssue` (`core/adapters`).
- Phase 1: `IssueTrackerType.Forgejo` exists (the adapter is keyed off it by the Phase 3
  caller, not referenced here).
- Not yet wired into any command — `CommandEnv`/`Issue.scala` dispatch is Phase 3.

**Testing:**
- Unit tests: 33 added in `ForgejoClientTest.scala`. Full `core.test` suite green; compile
  clean with `-Werror`, no warnings. All adapter tests network-free (`SyncBackendStub`).

**Code review:**
- Iterations: 1 (6 reviewers: scala3, testing, security, architecture, error-handling, style).
- Findings: 0 critical. Applied in-scope (confined to the two new files): defensive
  missing-field checks aligning with Linear/YouTrack; Scaladoc on all public methods;
  5 extra tests (create 404/422, validateToken 500, absent-body, body exact-keys); PURPOSE
  line reworded. Family-wide warnings deferred (see Decisions above).
- Review file: review-phase-02-20260624-133225.md

**For next phases:**
- Phase 3: add `fetchForgejoIssue`/`createForgejoIssue` to `TrackerOps`, implement in
  `LiveTrackerOps` delegating to `ForgejoClient`, extend `FakeTracker`; add the
  `IssueTrackerType.Forgejo` dispatch arm + `FORGEJO_API_TOKEN` resolution in `Issue.scala`.
- Phase 5: extend the adapter with PR creation + commit-status polling for forge parity.

**Files changed:**
```
A	core/adapters/ForgejoClient.scala
A	core/test/ForgejoClientTest.scala
```

---
