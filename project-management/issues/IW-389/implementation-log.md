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

## Phase 3: Capability wiring + command dispatch/auth (2026-06-24)

**Layer:** Application (capability port + command dispatch) — Layers 3 + 4

**What was built:**
- `core/model/Constants.scala` — `EnvVars.ForgejoApiToken = "FORGEJO_API_TOKEN"`, mirroring
  `LinearApiToken` / `YouTrackApiToken`.
- `core/commands/CommandEnv.scala` — added `fetchForgejoIssue(issueId, repository, baseUrl,
  token)` and `createForgejoIssue(repository, title, description, baseUrl, token)` to the
  `TrackerOps` port, mirroring the YouTrack signatures with `repository` added (GitLab shape).
- `core/commands/LiveCommandEnv.scala` — `LiveTrackerOps` implementations delegating to
  `ForgejoClient.fetchIssue` / `createIssue` (adapter `backend` defaulted).
- `core/commands/Issue.scala` — `forgejoToken(env)` helper reading `FORGEJO_API_TOKEN`;
  replaced the Phase-2 `Left("…not supported")` stubs with real `IssueTrackerType.Forgejo`
  dispatch arms (fetch + create) that resolve token + `repository` + required `baseUrl` from
  config; added Forgejo to the `resolveIssueId` teamPrefix arm so `iw issue 42` composes
  `PREFIX-42` (deliberate, review-confirmed parity with GitHub/GitLab — see Decisions).
- `core/test/fixtures/FakeCommandEnv.scala` — `FakeTracker` recorders for `fetchForgejoIssue`
  / `createForgejoIssue` (`kind = "forgejo"`, repository/baseUrl/token extras).

**Dependencies on other layers:**
- Phase 1: `IssueTrackerType.Forgejo`, `TrackerConfig` (`repository`, `trackerBaseUrl`).
- Phase 2: `ForgejoClient.{fetchIssue,createIssue}`, `CreatedIssue`.
- Domain: `Issue`, `IssueId`, `ApiToken`.

**Decisions:**
- `resolveIssueId` Forgejo teamPrefix arm added so a bare numeric arg composes `PREFIX-N`,
  matching GitHub/GitLab. Flagged in the phase context as a review-confirm behavior addition.
- Commands depend only on `TrackerOps`, never on `ForgejoClient` directly — preserves the test seam.
- `validateToken` left unconsumed here (needed by Phase 4 doctor/init).

**Testing:**
- Unit/harness tests: 10 Forgejo cases in `IssueHarnessTest.scala` driving `Issue` through
  `FakeCommandEnv`/`FakeTracker` — fetch (missing token/baseUrl/repository, happy path,
  numeric-prefix composition, adapter Left), create (happy path, missing token, and — added
  during review — missing baseUrl, missing repository). Full `core.test` green (186 suite,
  all pass); compile clean with `-Werror`, no warnings.

**Code review:**
- Iterations: 1 (6 reviewers: scala3, composition, architecture, testing, security, style).
- Findings: 0 critical. Applied in-scope: PURPOSE line in `Issue.scala` updated to include
  Forgejo; symmetric create-path tests (missing baseUrl + repository) added.
- Deferred (pre-existing / family-wide, own ticket): `TrackerOps` per-tracker method-pair
  growth → collapse behind a single `fetch/createIssue` pair with an `IssueTrackerConfig` ADT
  (matching the existing `ForgeType` discriminator on PR/CI methods); `ApiToken` opaque-type
  migration; `baseUrl` scheme/SSRF validation + `repository` path sanitization (Phase-2
  adapter); token-resolution helper duplication. Each matches the sibling tracker pattern this
  phase mirrors.
- Review file: review-phase-03-20260624-135813.md

**For next phases:**
- Phase 4: init (`--tracker=forgejo`, repository + baseUrl + optional teamPrefix, codeberg.org
  auto-detect) and doctor (Forgejo CI arm) integration; uses `validateToken`. + BATS smoke.
- Phase 5: extend the adapter + `ForgeType`/PR-creation paths for PR creation + CI-check
  polling so `phase-pr`/`phase-merge` work against Forgejo.

**Files changed:**
```
M	core/commands/CommandEnv.scala
M	core/commands/Issue.scala
M	core/commands/LiveCommandEnv.scala
M	core/model/Constants.scala
M	core/test/IssueHarnessTest.scala
M	core/test/fixtures/FakeCommandEnv.scala
```

---

## Phase 4: Init + doctor integration + smoke/harness coverage (2026-06-24)

**Layer:** Presentation / imperative shell (Init + Doctor commands) + cross-cutting test coverage — Layers 5 + 6

**What was built:**
- `core/commands/Init.scala` — four Forgejo arms filled in: `resolveTrackerType` accepts
  `--tracker=forgejo` (and the invalid-arg message now lists it); `askForTracker` gains a
  `5. Forgejo` menu option (`"5" | "forgejo"`); `collectTrackerDetails` replaces the Phase-1
  stub `Left(...)` with a real arm collecting `repository` + required `baseUrl` + optional
  `teamPrefix` (mirrors GitLab repo/prefix + YouTrack required-baseUrl, `team = ""`);
  `printNextSteps` prints the `export FORGEJO_API_TOKEN=...` prompt. PURPOSE comment updated.
- `core/model/Config.scala` — `TrackerDetector.suggestTracker` auto-detects `codeberg.org`
  remotes → `Forgejo` (RESOLVED clarify: codeberg-only; self-hosted instances selected
  explicitly via flag/menu).
- `core/commands/Doctor.scala` — `ciPlatform` gains a `Forgejo => "Forgejo Actions"` arm; the
  match was made exhaustive (`Linear | YouTrack` explicit, no wildcard) for compile-time safety.
- `core/model/CIChecks.scala` — Forgejo split out of the `Linear | YouTrack | Forgejo`
  placeholder into a dedicated Forgejo Actions arm checking `.forgejo/workflows/ci.yml`
  (primary) → `.github/workflows/ci.yml` (compat) → `Error("Missing", …)` (parity with
  GitHub/GitLab, not a Warning).
- `test/forgejo-issue.bats` (new) — BATS E2E smoke proving config-parse → dispatch → token
  resolution for Forgejo (hermetic no-token error-wiring variant; `IW_SERVER_DISABLED=1`).

**Decisions:**
- **`validateToken` NOT consumed** in init/doctor — no sibling tracker validates tokens at
  init/doctor time (Linear/YouTrack init only print the env-var prompt; Doctor's baseChecks are
  config/git-presence). Wiring it for Forgejo alone would diverge and pull network I/O into those
  commands. The method stays available on `ForgejoClient` for a future cross-tracker feature.
- **CIChecks missing workflow = Error** (forge parity with GitHub/GitLab), not Warning.
- **BATS smoke = hermetic no-token variant** — the adapter's HTTP/JSON behavior is already
  covered by Phase 2's 33 unit tests; the smoke proves wiring, not coverage.
- **Forgejo repository auto-detection and self-hosted host heuristics deferred** — RESOLVED
  clarify scopes auto-detect to *tracker-type* on `codeberg.org` only; repository extraction and
  self-hosted host matching are non-goals this phase.

**Dependencies on other layers:**
- Phase 1: `IssueTrackerType.Forgejo`, `TrackerTypeValues.Forgejo`, config serializer round-trip.
- Phase 2: `ForgejoClient` (consumed via dispatch by the BATS smoke; `validateToken` left unused).
- Phase 3: `EnvVars.ForgejoApiToken`, `TrackerOps`/`Issue.scala` Forgejo dispatch (exercised
  end-to-end by the smoke test; untouched here).

**Testing:**
- Unit/harness tests: 11 added — 5 Init (`InitHarnessTest`: all-args no-prompt, base-url prompt,
  next-steps token, codeberg auto-detect, menu option 5), 2 Doctor (`DoctorHarnessTest`:
  forgejoConfig valid-config, `--fix` "Forgejo Actions" CI label), 4 CIChecks (`CIChecksTest`:
  `.forgejo`, `.github` compat, neither→Error, both→prefers `.forgejo`). Full `core.test` green
  (186/186); `-Werror` compile clean, no warnings.
- E2E: 1 BATS smoke (`test/forgejo-issue.bats`), green.

**Code review:**
- Iterations: 1 (6 reviewers: scala3, composition, architecture, testing, security, style).
- Critical findings: 2 raised, 0 genuine blockers. (1) `os.pwd` in `CIChecks` model layer —
  pre-existing/family-wide (all arms), out of scope; deferred. (2) BATS commit-before-branching —
  test passes; applied the initial-commit hardening anyway for convention parity.
- Applied in-scope: PURPOSE comment, exhaustive Doctor match, `InitHarnessTest` stale-answer fix
  + field assertions on auto-detect/menu tests, `CIChecksTest` both-present case, BATS init-commit.
- Deferred (deliberate/family-wide, per analysis RESOLVED clarifications): Forgejo repo
  auto-detection, self-hosted host heuristic, baseUrl/repository pre-write validation.
- Review file: review-phase-04-20260624-143605.md

**For next phases:**
- Phase 5: extend the adapter + `ForgeType`/PR-creation paths for PR creation + CI-check polling
  so `phase-pr`/`phase-merge` work against Forgejo. The `ForgeType` discriminator does not yet
  have a Forgejo arm — that is the core of Phase 5.

**Files changed:**
```
M	core/commands/Doctor.scala
M	core/commands/Init.scala
M	core/model/CIChecks.scala
M	core/model/Config.scala
M	core/test/CIChecksTest.scala
M	core/test/DoctorHarnessTest.scala
M	core/test/InitHarnessTest.scala
A	test/forgejo-issue.bats
```

---

## Phase 5: PR creation + CI-check polling (forge parity) (2026-06-24)

**Layer:** Infrastructure / Application (forge paths)

**What was built:**
- `core/model/ForgeType.scala` - added `Forgejo` case; `cliTool`/`installUrl` became `Option[String]` (`None` for the CLI-less Forgejo forge); `fromHost("codeberg.org")` → `Forgejo`; `resolve` gives tracker-type precedence so a Forgejo tracker always resolves to the Forgejo forge regardless of remote host.
- `core/model/ForgeConfig.scala` (new) - pure carrier of optional `baseUrl`/`token` for forge ops, plus shared missing-config error-message constants.
- `core/model/ForgePullRequest.scala` (new) - pure `ForgePullRequest(number, htmlUrl, headSha)`.
- `core/model/ForgejoUrl.scala` (new) - pure URL parsers `extractPullRequestIndex` / `extractRepositoryFromPrUrl` (moved out of the adapter to honour FCIS).
- `core/model/PhaseMerge.scala` - `forgejoPrPattern` + Forgejo arm in `extractPrNumber`.
- `core/model/ProjectContext.scala` - Forgejo arms in exhaustive matches.
- `core/adapters/ForgejoClient.scala` - new HTTP methods `createPullRequest`, `mergePullRequest` (squash + delete branch), `fetchCheckStatuses`, plus `fetchPrHeadSha` (PR head-SHA lookup for CI polling), with pure URL/body builders and ujson parsers over the injectable `SyncBackend` seam.
- `core/commands/CommandEnv.scala` - additive `ForgeConfig` parameter on the four `TrackerOps` forge methods (Option B; `gitlabHost` retained).
- `core/commands/LiveCommandEnv.scala` - Forgejo arms for all four forge methods; shared `mergeForgejoSquash` helper.
- `core/commands/ForgeConfigResolver.scala` (new) - single shared resolver returning `Either[String, ForgeConfig]`; resolves `baseUrl` from config and token from `FORGEJO_API_TOKEN`, validating Forgejo prerequisites in the command layer (imperative shell).
- `core/commands/PhasePr.scala` / `PhaseMerge.scala` - resolve `ForgeConfig` inside the `Resolved` for-comprehension (fails fast, no non-local `return` guards).
- `core/commands/PhaseAdvance.scala` - CLI prerequisite guard handles `Option[String]` cliTool (skips for CLI-less Forgejo) + Forgejo arm in `checkMerged`.

**Key design decisions:**
- `ForgeType.resolve` precedence inversion: tracker type wins for Forgejo (self-hosted hosts are indistinguishable from self-hosted GitLab by hostname).
- CLI-less forge modelled honestly as `cliTool: Option[String] = None` rather than a fake CLI name.
- baseUrl/token contract gap resolved via Option B (additive `ForgeConfig` parameter resolved in the command layer); secret resolution stays at the edge.
- CI-status SHA: the Forgejo `fetchCheckStatuses` path does a `GET /pulls/{index}` lookup (`fetchPrHeadSha`) to read `head.sha`, keeping the shared signature minimal.

**Dependencies on other layers:**
- Phase 1 (`IssueTrackerType.Forgejo`, `trackerBaseUrl` config), Phase 2 (`ForgejoClient` issue methods + `SyncBackend` seam), Phase 3 (`FORGEJO_API_TOKEN`, `TrackerOps` seam + `FakeTracker`).

**Testing:**
- Unit: extended `ForgejoClientTest` (PR create incl. 200/201, merge incl. 204, check-status state mapping + empty, `fetchPrHeadSha`, URL/body builders, `extract*` parsers, 401/404/network-error mapping) and `ForgeTypeTest` (Forgejo resolution precedence + codeberg host + GitHub/GitLab regression).
- Harness: `PhasePrHarnessTest` / `PhaseMergeHarnessTest` Forgejo dispatch + token/baseUrl error-wiring; `FakeTracker` records a structured `CheckStatusCall` (forge/prNumber/repository/forgeConfig).
- E2E smoke: `test/forgejo-phase-pr.bats` hermetic no-token guard (`IW_SERVER_DISABLED=1`).
- Full `core.test` suite green; `scala-cli compile --scalac-option -Werror core/` clean (no warnings).

**Code review:**
- Iterations: 1 review pass (6 skills) + 1 fix pass. 0 critical correctness issues; fixed test-coverage gaps, a mislabeled harness test, cross-reviewer DRY duplication, an FCIS layer placement, and style cleanups. Deferred (noted): http:// cleartext-token warning, sttp redirect header policy, sealed `ForgeConfig`.
- Review file: review-phase-05-20260624-151810.md

**Files changed:**
```
M	core/adapters/ForgejoClient.scala
M	core/commands/CommandEnv.scala
A	core/commands/ForgeConfigResolver.scala
M	core/commands/LiveCommandEnv.scala
M	core/commands/PhaseAdvance.scala
M	core/commands/PhaseMerge.scala
M	core/commands/PhasePr.scala
A	core/model/ForgeConfig.scala
A	core/model/ForgePullRequest.scala
M	core/model/ForgeType.scala
A	core/model/ForgejoUrl.scala
M	core/model/PhaseMerge.scala
M	core/model/ProjectContext.scala
M	core/test/ForgeTypeTest.scala
M	core/test/ForgejoClientTest.scala
M	core/test/PhaseMergeHarnessTest.scala
M	core/test/PhasePrHarnessTest.scala
M	core/test/fixtures/FakeCommandEnv.scala
A	test/forgejo-phase-pr.bats
```

---
