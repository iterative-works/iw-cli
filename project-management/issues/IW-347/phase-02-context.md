# Phase 2: Repo link + stale-PR badge

**Issue:** IW-347
**Phase:** 2 of 2
**Type:** Implementation (domain + app/infra + presentation + frontend + tests)
**Estimate:** 3.25–7.25h

## Goals

- Add a persistent "open repo" link on every worktree card whenever
  `ProjectConfiguration.repository` is set, sitting next to the existing
  issue-id link so the git host page is always one click away.
- Stop the PR-link "flicker" perceived by the user: when the renderer is
  given PR data, always render the PR section. When the data behind it is
  past its TTL, mark it with a `· stale` badge (mirroring the existing
  `stale-indicator` pattern on issue data) instead of dropping the section.
- Land the long-flagged `youtrackBaseUrl` → `trackerBaseUrl` rename as part
  of the same change set so both the new `RepoUrlBuilder` and the existing
  `TrackerUrlBuilder` read from a clearly-named field.
- Keep the change additive and surgical — no fetch/eviction policy churn,
  no schema change, no new dependencies.

## Scope

### In scope

- New pure `iw.core.model.RepoUrlBuilder` (mirrors `TrackerUrlBuilder` shape).
- Field/parameter rename `youtrackBaseUrl` → `trackerBaseUrl` everywhere
  the identifier appears in Scala source and tests; HOCON file key
  (`tracker.baseUrl`) stays unchanged.
- New presentation-layer view model
  `iw.dashboard.presentation.views.PrDisplayData(pr, isStale)`.
- Application-layer plumbing in `WorktreeListSync` (and the matching
  `WorktreeCardService` path) to map `Option[CachedPR] + Instant`
  → `Option[PrDisplayData]` using `CachedPR.isValid`.
- `WorktreeCardRenderer.renderCard` signature change:
  `prData: Option[PullRequestData]` → `Option[PrDisplayData]`, plus a new
  `repoUrl: Option[String]` parameter; new repo-link section near the
  issue-id link; `· stale` badge inside the PR section when
  `prData.get.isStale`.
- CSS for the new repo-link button in the bundled
  `dashboard/jvm/resources/static/dashboard.css`.
- Tests written alongside each layer (TDD), plus updates to existing tests
  broken by the rename or the `prData` type change.
- Regenerate `docs/api/Config.md` and `docs/api/YouTrackClient.md` after
  the rename so the API docs stay accurate.

### Out of scope

- Any change to fetch/eviction policy, throttle behaviour, or cache
  population. The cache rework — including the symptom-C root cause
  identified in Phase 1 (`RefreshThrottle.recordRefresh` only firing on
  successful issue fetch) — is deferred to a separate parent issue per
  CLARIFY 3 in the analysis.
- Background warm-up, CLI-driven cache population, decoupling caches from
  dashboard visits — all on the follow-up issue.
- Removing the `WorktreeListView` dead code spotted during Phase 1.
- Backward-compat alias for the renamed field (CLARIFY 2: explicitly no
  alias; the CLI alias `iw config get youtrackBaseUrl` is removed).
- Branch-aware repo URLs (CLARIFY 1: repo root only).
- Touching `CachedPR.isValid` itself; reusing as-is.

## Architectural layer breakdown

### Domain layer (`core/model/`)

**Files to create:**

- `/home/mph/Devel/iw/iw-cli-IW-347/core/model/RepoUrlBuilder.scala`

**Files to modify:**

- `/home/mph/Devel/iw/iw-cli-IW-347/core/model/Config.scala` — rename the
  accessor (line 130), the `ProjectConfiguration.create` parameter
  (lines 149, 159), the HOCON serialisation site (line 208), and the
  HOCON parsing site (lines 267, 281). HOCON key `tracker.baseUrl`
  unchanged.
- `/home/mph/Devel/iw/iw-cli-IW-347/core/model/TrackerUrlBuilder.scala`
  — replace `config.youtrackBaseUrl` with `config.trackerBaseUrl`
  (lines 21, 26).

**API contract:**

```scala
object RepoUrlBuilder:
  /** Build the git repo root URL from the project's configuration.
    * Renders whenever `repository` is set, regardless of trackerType.
    * GitHub: https://github.com/{owner}/{repo}
    * GitLab: {trackerBaseUrl|https://gitlab.com}/{owner}/{repo}
    *         (handles nested groups: group/subgroup/project)
    * Linear/YouTrack with `repository` set: same rules apply
    *   (GitHub-style URL when repository looks like "owner/repo";
    *    callers can rely on this since `repository` is the host-agnostic
    *    handle in ProjectConfiguration). The exact mapping for non-Git*
    *    trackers will be table-driven by the test suite.
    */
  def buildRepoUrl(config: ProjectConfiguration): Option[String]
```

**Dependencies on other layers:** none. Pure derivation.

**Test coverage required:**

- Table-driven across all four `IssueTrackerType` values plus the
  "no repository configured" case.
- GitHub repo URL.
- GitLab on `gitlab.com` (no `trackerBaseUrl`).
- GitLab self-hosted via `trackerBaseUrl` (with and without trailing `/`).
- GitLab nested groups (`group/subgroup/project`).
- Linear- and YouTrack-tracked projects with `repository` set still
  return a sensible link (per CLARIFY 1: render whenever repository is
  defined).
- `None` when `repository` is missing.

### Application/Infrastructure layer (`dashboard/jvm/src/`)

**Files to create:**

- `/home/mph/Devel/iw/iw-cli-IW-347/dashboard/jvm/src/presentation/views/PrDisplayData.scala`
  (the type sits in `iw.dashboard.presentation.views` per CLARIFY 4 —
  it is a presentation concern, not a domain concept, even though it is
  constructed in the application layer).

**Files to modify:**

- `/home/mph/Devel/iw/iw-cli-IW-347/dashboard/jvm/src/WorktreeListSync.scala`
  — at the two `prData.map(_.pr)` sites (verified at line 136 in
  `generateAdditionOob` and line 224 in `generateReorderOob`; the analysis
  cited these line numbers and they are still accurate), replace the
  bare `_.pr` mapping with a mapping that builds a
  `PrDisplayData(cached.pr, isStale = !CachedPR.isValid(cached, now))`.
  Both sites already have `now: Instant` in scope.
- `/home/mph/Devel/iw/iw-cli-IW-347/dashboard/jvm/src/WorktreeCardService.scala`
  — the `prData` local at lines 149–172 currently holds
  `Option[PullRequestData]`. Convert to `Option[PrDisplayData]` so the
  `renderCard` call at line 191 receives the new type. The freshly
  fetched branch (line 152) produces `isStale = false`; the cached
  fall-back branches (lines 158, 164, 170) produce
  `isStale = !CachedPR.isValid(cached, now)`.
- `/home/mph/Devel/iw/iw-cli-IW-347/dashboard/jvm/src/WorktreeListView.scala`
  — `renderCard` call at line 114: caller currently passes `prData` of
  type `Option[PullRequestData]`. Update upstream so this site receives
  an `Option[PrDisplayData]`. (This may surface the fact that
  `WorktreeListView` is currently dead code — note in journal, do not
  delete here.)
- `/home/mph/Devel/iw/iw-cli-IW-347/dashboard/jvm/src/presentation/views/ProjectDetailsView.scala`
  — `renderCard` call at line 183: same change to `prData` propagation,
  plus thread the new `repoUrl: Option[String]` argument from the
  configuration available in this view's caller.
- All Scala files referencing `youtrackBaseUrl` in the dashboard layer:
  `dashboard/jvm/src/DashboardService.scala` (lines 157 comment, 231,
  296, 301), `dashboard/jvm/src/CaskServer.scala` (lines 337 comment,
  1307, 1346, 1385, 1421, 1459, 1502), `dashboard/jvm/src/IssueSearchService.scala`
  (lines 164, 175).

**API contracts:**

```scala
// dashboard/jvm/src/presentation/views/PrDisplayData.scala
package iw.dashboard.presentation.views

final case class PrDisplayData(
    pr: PullRequestData,
    isStale: Boolean
)
```

The mapping from `Option[CachedPR]` to `Option[PrDisplayData]` happens
inline at each call site (no helper needed yet — only two sites in
`WorktreeListSync`, plus the `WorktreeCardService` cases).

**Dependencies on other layers:** consumes `RepoUrlBuilder` and the
renamed `trackerBaseUrl` from the domain layer; produces `PrDisplayData`
for the presentation layer.

**Test coverage required:**

- Add cases to `dashboard/jvm/test/src/WorktreeListSyncTest.scala`
  exercising the staleness mapping at both call sites: fresh `CachedPR`
  produces `PrDisplayData(_, isStale = false)`; expired `CachedPR`
  produces `PrDisplayData(_, isStale = true)`; missing cache produces
  `None`.
- Update existing `WorktreeListSyncTest`, `WorktreeCardServiceTest`, and
  any other tests whose call sites are affected by the renderer
  parameter change.

### Presentation layer (`dashboard/jvm/src/presentation/views/`)

**Files to modify:**

- `/home/mph/Devel/iw/iw-cli-IW-347/dashboard/jvm/src/presentation/views/WorktreeCardRenderer.scala`
  — change `renderCard` signature; add the repo-link section near the
  issue-id link (currently rendered at lines 127–133); add the `· stale`
  badge inside the existing PR section (lines 146–160), reusing the
  `stale-indicator` pattern at lines 215–220 verbatim for class name and
  text.

**API contract (new `renderCard` signature):**

```scala
def renderCard(
    worktree: WorktreeRegistration,
    data: IssueData,
    fromCache: Boolean,
    isStale: Boolean,
    progress: Option[WorkflowProgress],
    gitStatus: Option[GitStatus],
    prData: Option[PrDisplayData],          // was Option[PullRequestData]
    reviewStateResult: Option[Either[String, ReviewState]],
    now: Instant,
    sshHost: String,
    htmxConfig: HtmxCardConfig,
    repoUrl: Option[String] = None          // NEW
): Frag
```

The repo-link section sits immediately after the issue-id `<p>` (line
133) and renders a `<p class="repo-link"><a class="repo-button"
href="${url}" target="_blank">Repo</a></p>` (or similar — exact markup
to be settled at implementation time, sibling shape to the existing
`pr-link` block at line 147). The section is omitted entirely when
`repoUrl == None`.

The PR section's `· stale` badge is appended as a `<span
class="stale-indicator"> · stale</span>` inside the existing
`div.pr-link` (next to the `pr-badge`). It renders only when
`prData.get.isStale == true`.

**Dependencies on other layers:** consumes `PrDisplayData` from the
application/infra layer and `repoUrl: Option[String]` derived from
`RepoUrlBuilder`.

**Test coverage required:**

- `WorktreeCardRendererTest.scala` (existing, lines 39, 62, 85, 109 all
  call `renderCard`):
  - Repo link rendered when `repoUrl = Some(url)`; absent when `None`.
  - PR section rendered when `prData = Some(_)` regardless of
    `isStale`.
  - `· stale` badge present iff `prData.get.isStale == true`.
- Update existing fixtures that pass a bare `PullRequestData` to wrap it
  in `PrDisplayData(pr, isStale = false)`.

### Frontend layer (`dashboard/`)

**File to modify:**

- `/home/mph/Devel/iw/iw-cli-IW-347/dashboard/jvm/resources/static/dashboard.css`
  — add a `.repo-button` style (sibling to `.pr-button` at line 235;
  matching the visual treatment of `.pr-button` at line 235 and
  `.pr-button:hover` at line 246). The frontend Tailwind shell at
  `dashboard/frontend/src/main.css` is only an `@import`/`@source`
  directive — actual rules live in `dashboard/jvm/resources/static/dashboard.css`,
  which is the file to touch.
- The existing `.stale-indicator` rule (line 138) is reused verbatim —
  no new CSS needed for the stale-PR badge.

**Dependencies on other layers:** consumes the class names emitted by
the renderer.

**Test coverage required:** none (CSS-only, visual smoke check via
`./iw dashboard` is sufficient).

### Testing layer

**Files to create:**

- `/home/mph/Devel/iw/iw-cli-IW-347/core/test/RepoUrlBuilderTest.scala`
  — table-driven coverage matching the cases in `TrackerUrlBuilderTest.scala`.

**Files to modify (rename impact):**

- `/home/mph/Devel/iw/iw-cli-IW-347/core/test/ConfigFileTest.scala`
  (lines 50, 77, 98).
- `/home/mph/Devel/iw/iw-cli-IW-347/core/test/ConfigTest.scala`
  (lines 643, 681, 699, 794, 803, 920, 928, 937).
- `/home/mph/Devel/iw/iw-cli-IW-347/core/test/TrackerUrlBuilderTest.scala`
  (lines 53, 63 — note: the analysis did not list this file explicitly;
  it is included here because the identifier appears in the test
  builders).
- `/home/mph/Devel/iw/iw-cli-IW-347/core/test/TestFixtures.scala`
  (line 163).
- `/home/mph/Devel/iw/iw-cli-IW-347/dashboard/jvm/test/src/IssueSearchServiceTest.scala`
  (lines 131, 263).
- `/home/mph/Devel/iw/iw-cli-IW-347/test/config.bats` (lines 137, 138,
  152) — BATS E2E test using the old field name.

**Files to modify (renderer impact):**

- `/home/mph/Devel/iw/iw-cli-IW-347/dashboard/jvm/test/src/WorktreeCardServiceTest.scala`
  (15 `renderCard` call sites at lines 44, 68, 100, 136, 195, 247, 315,
  382, 433, 505, 570, 608, 661, 725, 784).
- `/home/mph/Devel/iw/iw-cli-IW-347/dashboard/jvm/test/src/WorktreeCardRendererTest.scala`
  (4 call sites at lines 39, 62, 85, 109).
- `/home/mph/Devel/iw/iw-cli-IW-347/dashboard/jvm/test/src/WorktreeListSyncTest.scala`
  (existing tests covering the two call sites being changed).

## Implementation sequence

The order below mirrors the recommended layer order from the analysis
and Phase 1's notes:

1. **Domain layer.** Land the `youtrackBaseUrl` → `trackerBaseUrl` rename
   first — single mechanical sweep, compiler driven. Run
   `scala-cli compile --scalac-option -Werror core/` and the dashboard
   build to confirm no caller is missed. Then add `RepoUrlBuilder` plus
   `RepoUrlBuilderTest.scala` (TDD: write the test cases first).
   Update `commands/config.scala` so the CLI alias map (line 38,
   `"youtrackBaseUrl" -> List("tracker", "baseUrl")`) and help text
   (line 120) reflect the rename — the alias is removed, not aliased.
   Update `commands/issue.scala` (lines 173, 255) and `commands/init.scala`
   (lines 98, 242).

2. **Application/Infrastructure layer.** Add `PrDisplayData.scala`.
   Update the two staleness-mapping points in `WorktreeListSync.scala`
   (the `prData.map(_.pr)` expressions at lines 136 and 224, inside
   the `renderCard(...)` calls that begin at lines 129 and 217), then
   `WorktreeCardService.scala` lines 149–172, then `WorktreeListView.scala`
   line 114 and `ProjectDetailsView.scala` line 183. Wire the
   `repoUrl: Option[String]` argument through the call sites that have
   access to `ProjectConfiguration` — for the dashboard render path
   that's `CaskServer` (lines 365, 440 already pull config) feeding
   `WorktreeCardService.renderCard`, and the project-details view via
   its existing config plumbing.

3. **Presentation layer.** Update `WorktreeCardRenderer.renderCard`
   signature and body: add the repo-link section after line 133, and
   the `· stale` badge inside the PR block. Update the
   `WorktreeCardRendererTest` cases (TDD: red → green).

4. **Frontend layer.** Add `.repo-button` rule(s) to
   `dashboard/jvm/resources/static/dashboard.css` next to `.pr-button`.
   Visual smoke check: `./iw dashboard` and look at a card with
   `repository` configured.

5. **Test cleanup.** Sweep the test files listed under "Files to modify
   (renderer impact)" so any remaining call sites compile against the
   new `renderCard` shape, and update fixtures that still pass a bare
   `PullRequestData`. Run the full unit suite and BATS E2E
   (`./iw ./test`).

6. **Doc regeneration.** Regenerate `docs/api/Config.md` and
   `docs/api/YouTrackClient.md` per CLARIFY 2.

## Files this phase WILL modify

Domain + commands + tests (rename radius + new builder integration):

- `/home/mph/Devel/iw/iw-cli-IW-347/core/model/Config.scala`
- `/home/mph/Devel/iw/iw-cli-IW-347/core/model/TrackerUrlBuilder.scala`
- `/home/mph/Devel/iw/iw-cli-IW-347/commands/config.scala`
- `/home/mph/Devel/iw/iw-cli-IW-347/commands/issue.scala`
- `/home/mph/Devel/iw/iw-cli-IW-347/commands/init.scala`
- `/home/mph/Devel/iw/iw-cli-IW-347/core/test/ConfigFileTest.scala`
- `/home/mph/Devel/iw/iw-cli-IW-347/core/test/ConfigTest.scala`
- `/home/mph/Devel/iw/iw-cli-IW-347/core/test/TrackerUrlBuilderTest.scala`
- `/home/mph/Devel/iw/iw-cli-IW-347/core/test/TestFixtures.scala`
- `/home/mph/Devel/iw/iw-cli-IW-347/test/config.bats`

Dashboard application + presentation + tests:

- `/home/mph/Devel/iw/iw-cli-IW-347/dashboard/jvm/src/DashboardService.scala`
- `/home/mph/Devel/iw/iw-cli-IW-347/dashboard/jvm/src/CaskServer.scala`
- `/home/mph/Devel/iw/iw-cli-IW-347/dashboard/jvm/src/IssueSearchService.scala`
- `/home/mph/Devel/iw/iw-cli-IW-347/dashboard/jvm/src/WorktreeCardService.scala`
- `/home/mph/Devel/iw/iw-cli-IW-347/dashboard/jvm/src/WorktreeListSync.scala`
- `/home/mph/Devel/iw/iw-cli-IW-347/dashboard/jvm/src/WorktreeListView.scala`
- `/home/mph/Devel/iw/iw-cli-IW-347/dashboard/jvm/src/presentation/views/ProjectDetailsView.scala`
- `/home/mph/Devel/iw/iw-cli-IW-347/dashboard/jvm/src/presentation/views/WorktreeCardRenderer.scala`
- `/home/mph/Devel/iw/iw-cli-IW-347/dashboard/jvm/test/src/WorktreeCardRendererTest.scala`
- `/home/mph/Devel/iw/iw-cli-IW-347/dashboard/jvm/test/src/WorktreeCardServiceTest.scala`
- `/home/mph/Devel/iw/iw-cli-IW-347/dashboard/jvm/test/src/WorktreeListSyncTest.scala`
- `/home/mph/Devel/iw/iw-cli-IW-347/dashboard/jvm/test/src/IssueSearchServiceTest.scala`

Frontend:

- `/home/mph/Devel/iw/iw-cli-IW-347/dashboard/jvm/resources/static/dashboard.css`

Generated API docs (regenerated, not hand-edited):

- `/home/mph/Devel/iw/iw-cli-IW-347/docs/api/Config.md`
- `/home/mph/Devel/iw/iw-cli-IW-347/docs/api/YouTrackClient.md`

## Files this phase WILL create

- `/home/mph/Devel/iw/iw-cli-IW-347/core/model/RepoUrlBuilder.scala`
- `/home/mph/Devel/iw/iw-cli-IW-347/core/test/RepoUrlBuilderTest.scala`
- `/home/mph/Devel/iw/iw-cli-IW-347/dashboard/jvm/src/presentation/views/PrDisplayData.scala`

No CSS partial is created — the existing
`dashboard/jvm/resources/static/dashboard.css` is the bundled stylesheet
and gets the `.repo-button` rules appended.

## Dependencies on prior phases

Phase 1 produced two markdown deliverables (`cache-investigation.md`,
`cache-rework-issue-draft.md`) and is merged. Phase 1's findings
**inform** Phase 2 but do not block it:

- The Phase 1 write-up confirmed the **symptom-C root cause** lives in
  `WorktreeCardService.scala:108`: `RefreshThrottle.recordRefresh` only
  fires after a successful issue fetch, so a tracker-auth blip leaves
  the throttle open and the next poll re-fetches PR data — which can
  return `Right(None)` and produce a render with `prData = None`. **This
  root cause is explicitly NOT fixed in IW-347** (CLARIFY 3 deferred
  the cache rework). Phase 2 only mitigates the user-visible symptom by
  marking stale PR data in the renderer instead of dropping the
  section.
- The Phase 1 write-up also confirmed `CachedPR.isValid` exists at
  `core/model/CachedPR.scala:48` with the expected signature
  `(cached: CachedPR, now: Instant): Boolean` — Phase 2 reuses it as-is.
- The Phase 1 finding that `WorktreeListView` is unused on the live
  route map is **noted, not acted on** in this phase.

## Acceptance criteria

- [ ] `RepoUrlBuilder.buildRepoUrl(config)` returns a non-`None` URL
  whenever `config.repository.isDefined`, regardless of `trackerType`,
  and uses `config.trackerBaseUrl` for self-hosted GitLab with
  `https://gitlab.com` as fallback.
- [ ] `RepoUrlBuilder` returns `None` when `repository` is unset.
- [ ] `WorktreeCardRenderer.renderCard` renders the repo-link section
  iff `repoUrl.isDefined`.
- [ ] `WorktreeCardRenderer.renderCard` renders the PR section whenever
  `prData.isDefined` and adds the `· stale` indicator iff
  `prData.get.isStale == true`. The PR section is **not** dropped when
  `isStale` is true.
- [ ] `youtrackBaseUrl` no longer appears as a Scala identifier
  anywhere in `core/`, `commands/`, `dashboard/jvm/`, or test source
  trees. (Documents under `project-management/issues/` and historical
  review files are left as-is.)
- [ ] HOCON key `tracker.baseUrl` continues to load and round-trip
  through `ProjectConfiguration` parsing/serialisation unchanged.
- [ ] `scala-cli compile --scalac-option -Werror core/` passes.
- [ ] `./mill dashboard.test` and `./mill dashboard.itest.testForked`
  pass.
- [ ] `./iw ./test` (full unit + BATS E2E) passes.
- [ ] `docs/api/Config.md` and `docs/api/YouTrackClient.md` are
  regenerated and reflect `trackerBaseUrl`.
- [ ] Visual smoke check on `./iw dashboard`: a card with
  `repository` configured shows a "Repo" button next to the issue-id;
  a card with stale PR cache shows `· stale` next to the PR badge.

## Notes for the implementer

- **BATS rename target.** `test/config.bats:137`, `:138`, `:152` is
  the BATS test "config get youtrackBaseUrl when unset returns error" —
  this entire test should be renamed to use `trackerBaseUrl`. Do NOT
  preserve a backward-compat alias (CLARIFY 2 explicit decision).
- **CLI alias map.** `commands/config.scala:38` has the dotted-path
  alias `"youtrackBaseUrl" -> List("tracker", "baseUrl")`. Replace the
  entry with `"trackerBaseUrl" -> List("tracker", "baseUrl")`. The
  alias mechanism stays; only the old name `youtrackBaseUrl` is
  removed (CLARIFY 2: no backward-compat alias). After this change
  `iw config get trackerBaseUrl` resolves through the same
  alias-to-dotted-path lookup that `youtrackBaseUrl` used before.
- **`docs/api/` regeneration.** `docs/api/Config.md` (line 28) and
  `docs/api/YouTrackClient.md` (lines 50, 59) currently embed
  `youtrackBaseUrl`. These are generated; regenerate after the rename
  using whatever pipeline produced them (likely `build-iw-cli-skills`
  or a sibling generator — check `.claude/skills/build-iw-cli-skills`).
- **Renderer call sites — five direct callers** of
  `WorktreeCardRenderer.renderCard` to update for the `prData` type
  change and new `repoUrl` parameter: `WorktreeListSync.scala:129`
  and `:217`, `ProjectDetailsView.scala:183`,
  `WorktreeListView.scala:114`, and `WorktreeCardService.scala:191`.
  In addition, the parameter cascade through `WorktreeCardService.renderCard`
  means its two callers in `CaskServer.scala` (lines 365 and 440)
  must also pass the new `repoUrl` argument. Use the compiler to
  confirm none is missed.
- **`WorktreeListView` is dead code on the live route map** (Phase 1
  finding). Keep updating its `renderCard` call to keep the build
  green; do not delete it as part of this phase.
- **Tailwind v4 frontend is not the place for the new CSS.** The
  bundled stylesheet that ships in `dashboard/jvm/resources/static/dashboard.css`
  is what the dashboard serves; the `dashboard/frontend/src/main.css`
  file is a 5-line Tailwind shell. Add `.repo-button` to the served
  stylesheet next to `.pr-button` (line 235) and reuse `.stale-indicator`
  (line 138) verbatim for the PR-stale badge.
- **Where `repoUrl` enters the request path.** `CaskServer.scala`
  already loads `ProjectConfiguration` per request before calling
  `WorktreeCardService.renderCard` (lines 365 and 440). Compute the
  `repoUrl` there and forward it through; mirror the existing pattern
  for tracker-config plumbing in those handlers.
- **`CachedPR` already exposes `isStale`** (line 76, in addition to
  `isValid`). Either is fine for the staleness check; the analysis
  picked `!CachedPR.isValid(cached, now)` so prefer that for textual
  alignment. Both are equivalent at the boundary
  `age >= ttlMinutes`.
- **Renaming the parameter on `ProjectConfiguration.create`** is a
  breaking API change for any external caller, but `create` is only
  called inside this repo. The compiler will catch every call site.
- **Tests-alongside, not after.** The renderer test asserting the new
  behaviour should be written first (red), then the renderer change
  made to drive it green (per project TDD policy).
