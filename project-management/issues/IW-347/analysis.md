# Technical Analysis: Dashboard — Add git repo web link and ensure PR link is always visible

**Issue:** IW-347
**Created:** 2026-04-28
**Status:** Draft

## Problem Statement

Worktree cards in the dashboard are the user's primary control surface for active work, but two small gaps make them harder to use than they should be:

1. **No direct path to the git repo web page.** The card already links to the issue tracker, but to reach the GitHub/GitLab page (to glance at the branch, open a compare view, or copy a clone URL) the user has to navigate manually.
2. **PR link disappears intermittently.** When the PR cache hasn't been populated yet (or, hypothetically, has been actively evicted), the card renders without a PR link even when a PR exists. Users notice the link "flickering in" after a refresh tick.

This is a Layer 1 ("Fix the Annoying Stuff") parent under #343 — the value is removing two papercuts that erode trust in the dashboard as a control plane. Both fixes should be small and surgical.

## Proposed Solution

### High-Level Approach

Add a pure `RepoUrlBuilder` in `core/model/` mirroring `TrackerUrlBuilder`, deriving a repo-root URL from `ProjectConfiguration` only (no branch). Rename the misnamed `youtrackBaseUrl` accessor to `trackerBaseUrl` so both builders read from a clearly-named field. Plumb the URL through to `WorktreeCardRenderer` as a new optional parameter, rendered as a persistent button near the issue-id link.

For PR-link visibility: rather than patching the cache, first run a time-boxed investigation pass to characterise the wider caching-architecture problem (caches populate only on dashboard visit; CLI tools see nothing for unvisited worktrees; first-visit fetch lags ~30 s). Capture the findings in a write-up and open a separate parent issue for the cache rework. Within IW-347, the only behavioural change to PR display is presentational: thread the existing staleness signal (`!CachedPR.isValid(cached, now)`) through to the renderer via a new `PrDisplayData(pr, isStale)` view model, and render a `· stale` badge whenever the cache happens to hold stale data. No fetch or eviction policy changes.

### Why This Approach

- Mirroring `TrackerUrlBuilder` keeps URL construction pure, testable, and consistent with existing conventions.
- Renaming `youtrackBaseUrl` → `trackerBaseUrl` removes a known misnomer flagged by a previous code review; the on-disk file format is unchanged, so the rename is purely internal cleanup.
- Putting `PrDisplayData` in `iw.dashboard.presentation.views` (not `core/model/`) keeps display concerns out of the domain layer; the staleness check is computed by the existing `CachedPR.isValid` and made into a presentation-ready value at the application boundary.
- Deferring the cache-architecture fix to its own issue avoids shipping a band-aid that masks a deeper problem and ensures the user-visible CLI gap is addressed at the root.

## Architecture Design

### Domain Layer (`core/model/`)

**Components:**
- `RepoUrlBuilder` — pure object with `buildRepoUrl(config: ProjectConfiguration): Option[String]`, mirroring `TrackerUrlBuilder` shape.
- Rename: accessor `ProjectConfiguration.youtrackBaseUrl` → `trackerBaseUrl` (and `ProjectConfiguration.create`'s parameter), per CLARIFY 2. HOCON file key (`tracker.baseUrl`) is unchanged.

**Responsibilities:**
- Construct repo root URLs for GitHub and GitLab from `ProjectConfiguration.repository` and (for self-hosted GitLab) `trackerBaseUrl`.
- Render whenever `repository.isDefined`, regardless of `trackerType`; return `None` when `repository` is not set.

**Estimated Effort:** 1.0–2.0h (includes the `trackerBaseUrl` rename sweep)
**Complexity:** Straightforward

---

### Application/Infrastructure Layer (`dashboard/jvm/src/`)

**Components:**
- Update `WorktreeCardService` (or wherever card data is composed) to compute the repo URL via `RepoUrlBuilder` and forward it to the renderer.
- Update `WorktreeListSync.scala` lines 136 and 224 (the two call sites that currently do `prData.map(_.pr)`): map `Option[CachedPR]` + `now` → `Option[PrDisplayData(pr, isStale)]` using `CachedPR.isValid`, and forward to the renderer.
- **No fetch / eviction policy changes** in IW-347 (CLARIFY 3 deferred the cache-architecture work to a separate parent issue).

**Responsibilities:**
- Orchestrate config + cached PR into the data the renderer needs.
- Preserve staleness signal end-to-end instead of dropping it on the way to view code.

**Estimated Effort:** 0.75–1.5h
**Complexity:** Straightforward (no behavioural change to caching itself)

---

### Presentation Layer (`dashboard/jvm/src/presentation/views/`)

**Components:**
- New `PrDisplayData(pr: PullRequestData, isStale: Boolean)` view model in `iw.dashboard.presentation.views`.
- `renderCard` parameter change: `prData: Option[PullRequestData]` → `prData: Option[PrDisplayData]`.
- New optional `repoUrl: Option[String]` parameter on `renderCard`.
- New "repo link" section rendered near the issue-id link.
- Stale badge on the PR section reusing the `stale-indicator` text/CSS pattern (line 215–220 of the renderer).

**Responsibilities:**
- Render the repo link as a persistent button when URL is available; do nothing when None.
- Always render the PR section when PR data is present (already true) and add a `· stale` indicator when `prData.get.isStale` is true.

**Estimated Effort:** 0.5–1.5h
**Complexity:** Straightforward

---

### Frontend Layer (`dashboard/frontend/`)

**Components:**
- CSS for the new repo-link button (likely a sibling to `.pr-button`).
- CSS tweak for a stale-PR indicator if the existing `.stale-indicator` style isn't reusable verbatim.

**Responsibilities:**
- Visual styling consistent with existing card buttons.

**Estimated Effort:** 0.25–0.75h
**Complexity:** Straightforward

---

### Testing Layer

**Components:**
- Unit tests for `RepoUrlBuilder` — table-driven across all four `IssueTrackerType` values plus the "no repository configured" case, GitLab self-hosted with `trackerBaseUrl`, and nested GitLab paths.
- Renderer tests asserting the repo link is present when URL is provided and absent when not, and the stale-PR badge appears only when `prData.get.isStale` is true.
- Unit/integration tests covering the staleness mapping in `WorktreeListSync` (stale `CachedPR` produces `PrDisplayData(_, isStale=true)`; fresh `CachedPR` produces `isStale=false`).
- Update existing tests broken by the `youtrackBaseUrl` → `trackerBaseUrl` rename (`core/test/ConfigFileTest.scala`, `test/config.bats:137`).

**Estimated Effort:** 0.75–1.5h
**Complexity:** Straightforward

---

## Technical Decisions

### Patterns

- Pure URL builder mirroring `TrackerUrlBuilder` — keeps domain layer free of I/O.
- Functional core / imperative shell — URL derivation in `model/`, view-model construction and composition in `dashboard/`.
- Presentation-layer view model (`PrDisplayData`) keeps display concerns out of `core/model/` while still providing a self-describing renderer input.

### Technology Choices

- **Frameworks/Libraries:** No new dependencies. Scalatags for the new card section, existing CSS for styling.
- **Data Storage:** None — config already provides `repository`; cache plumbing is already in place.
- **External Systems:** None new. Repo URLs are static derivations; no I/O is required to compute them.

### Integration Points

- `RepoUrlBuilder` consumes `ProjectConfiguration` (already loaded by dashboard at startup).
- `WorktreeListSync` maps `Option[CachedPR] + now` → `Option[PrDisplayData]` and threads it (plus `repoUrl`) into `WorktreeCardRenderer.renderCard`.
- No fetch / cache policy changes in this issue.

## Resolved Clarifications

### Resolved: Repo URL target shape per tracker type

**Decision:** Repo root only (the simplest target). Branch pages and compare views may be added later.

- `RepoUrlBuilder.buildRepoUrl(config: ProjectConfiguration): Option[String]` — no branch parameter.
- GitHub: `https://github.com/{owner}/{repo}`
- GitLab (gitlab.com or self-hosted via `trackerBaseUrl` — see CLARIFY 2 resolution): `{baseUrl}/{owner}/{repo}` (or `{group}/{subgroup}/{project}` for nested GitLab paths).
- Render the repo link whenever `repository.isDefined`, regardless of `trackerType` (so a Linear- or YouTrack-tracked project with code on GitHub/GitLab still gets the link).

**Implications for downstream items:**
- This forces CLARIFY 5 toward a no-branch builder API — resolved together below.
- Drops ~0.5h from the domain layer estimate and ~0.25h from tests.

---

### Resolved: Source of git host base URL for self-hosted GitLab

**Decision:** Mirror the existing overload (one field used as base URL for both YouTrack and self-hosted GitLab), and rename the misnamed accessor `youtrackBaseUrl` → `trackerBaseUrl` as part of this issue. No backward-compat alias.

- HOCON file key (`tracker.baseUrl`) is already clean — no file-format change.
- Rename touches: `core/model/Config.scala` (accessor + `ProjectConfiguration.create` factory parameter), `core/model/TrackerUrlBuilder.scala`, `commands/config.scala` (CLI alias map), `commands/issue.scala`, `commands/init.scala`, `core/test/ConfigFileTest.scala`, BATS test `test/config.bats:137`.
- Regenerate `docs/api/Config.md` and `docs/api/YouTrackClient.md` after the rename.
- `RepoUrlBuilder` consumes `config.trackerBaseUrl` for self-hosted GitLab, with `https://gitlab.com` as the fallback (mirrors `TrackerUrlBuilder`).

**Estimate impact:** +0.5–1h to the domain/infra phase for the mechanical rename.

---

### Resolved: Root cause of "missing PR link due to cache timing"

**Decision:** Investigate first; do **not** ship a behavioural fix as part of IW-347.

The observed "PR link missing" symptom is one face of a wider caching-architecture problem reported by Michal:

- The PR / issue / git-status caches are **dashboard-bound** — they populate only when the dashboard is visited.
- CLI tools that read worktree state for worktrees the user has not visited on the dashboard see no cached data at all.
- Even on the dashboard, the **first** view of a not-yet-cached worktree blocks/lags for ~30 s while data is fetched on demand.
- There is no background fill or scheduled warm-up independent of dashboard visits.

This is a deeper design issue than what IW-347 was scoped for, and a one-line "kick a background fetch on first cache miss" patch would mask the root cause without addressing the CLI-visibility gap.

**Action under IW-347:**
1. Time-box a ~1 h investigation pass: grep cache code (`PullRequestCacheService`, `IssueCacheService`, `GitStatusService`, `RefreshThrottle`, `ServerStateService`), document who writes the caches, when, and what triggers refreshes; reproduce the user-visible symptoms (CLI shows nothing for unvisited worktree; first dashboard visit lags ~30 s; PR link flicker).
2. **Surface findings to Michal** as a write-up under `project-management/issues/IW-347/cache-investigation.md`. Do not change code.
3. **Open a separate parent issue** for the cache-architecture rework (background warm-up, CLI-driven cache population, decoupling from dashboard visits). Link IW-347 as one of its motivating cases.
4. Phase 2 of IW-347 then narrows to: **renderer-only** stale-PR badge plumbing — preserve the staleness flag end-to-end so that *whenever* the cache happens to hold stale data, the user sees it labelled as such. No changes to fetch/eviction policy.

**Phase plan impact:**
- Phase 1 (repo link) unchanged.
- Phase 2 shrinks to: staleness-flag plumbing in `WorktreeListSync.scala:136`/`:224`, renderer stale badge, integration test for "stale cache renders with badge". Estimate: 1.25–2.25 h. Investigation pass: 0.75–1.25 h, separate from Phase 2 estimate.
- Total IW-347 estimate revised downward (see Total Estimates section).

---

### Resolved: How to thread PR staleness to the renderer

**Decision:** Introduce a presentation-layer view model `PrDisplayData(pr: PullRequestData, isStale: Boolean)` in package `iw.dashboard.presentation.views` (next to `WorktreeCardRenderer`). Do **not** put it in `core/model/`.

**Rationale:**
- Staleness is computed by the existing pure function `CachedPR.isValid(cached, now)` — no new domain logic needed.
- The decision (`isStale = !CachedPR.isValid(cached, now)`) is *made* in the application layer, where `WorktreeListSync` holds the `Option[CachedPR]` and the current `Instant`.
- What the renderer consumes is "PR data + how to display it" — a presentation concern, not a domain concept. Keeping the type next to the view avoids polluting `core/model/`.

**Data flow:**
```
WorktreeListSync (application layer)
  Option[CachedPR] + now  →  Option[PrDisplayData(pr, isStale)]
                              ↓
WorktreeCardRenderer.renderCard(prData: Option[PrDisplayData], …)
```

**Renderer change:** `prData: Option[PullRequestData]` becomes `prData: Option[PrDisplayData]`. Stale variant adds a `· stale` indicator on the existing PR section, mirroring the existing `stale-indicator` styling.

---

### Resolved: Branch-name source for repo URL

**Decision:** Collapsed by CLARIFY 1's "repo root only" choice.

`RepoUrlBuilder.buildRepoUrl(config: ProjectConfiguration): Option[String]` takes no branch parameter. No branch sourcing required at any layer.

---

## Total Estimates

**Per-Layer Breakdown (IW-347 implementation):**
- Domain Layer (`RepoUrlBuilder` + `trackerBaseUrl` rename): 1.0–2.0h
- Application/Infrastructure Layer (URL plumbing + `PrDisplayData` mapping; no fetch/eviction changes): 0.75–1.5h
- Presentation Layer (repo link + stale-PR badge): 0.5–1.5h
- Frontend Layer (CSS): 0.25–0.75h
- Testing Layer: 0.75–1.5h

**Out-of-band investigation pass (no code, deliverable is a write-up):**
- Cache-architecture investigation: 0.75–1.25h → produces `project-management/issues/IW-347/cache-investigation.md`, motivates a separate parent issue.

**Total Range:** 3.25 – 7.25 hours of implementation, plus 0.75–1.25 h investigation.

**Confidence:** Medium-High (post-clarification)

**Reasoning:**
- Most ranges narrowed after clarifications; only the rename adds variance because of the touch radius.
- No new dependencies, no schema migrations, no protocol changes.
- Cache architecture work is explicitly deferred to a separate issue; IW-347 ships only the parts that are safe without that wider rework.

## Recommended Phase Plan

After CLARIFY 3 narrowed Phase 2 to renderer-only stale-badge plumbing, the implementation becomes small enough that splitting it across two phases would create churn rather than clarity. The investigation pass, by contrast, is a distinct deliverable (a write-up that motivates a separate parent issue) and benefits from being kept separate so it can land first.

- **Phase 1: Cache-architecture investigation (write-up only, no code)**
  - Includes: grep cache code (`PullRequestCacheService`, `IssueCacheService`, `GitStatusService`, `RefreshThrottle`, `ServerStateService`); document who writes the caches, when, and what triggers refreshes; reproduce the symptoms (CLI shows nothing for unvisited worktrees; first dashboard visit lags ~30 s; PR link flicker).
  - Deliverable: `project-management/issues/IW-347/cache-investigation.md` plus a draft for a new parent issue covering background warm-up / CLI-driven cache population.
  - Estimate: 0.75–1.25h
  - Rationale: Below the usual 3h phase floor, but it is a discovery phase that produces a write-up rather than code; the floor reasoning is about review/PR overhead, which doesn't apply.

- **Phase 2: Repo link + stale-PR badge (Domain + App/Infra plumbing + Presentation + Frontend + Tests)**
  - Includes: `RepoUrlBuilder` in `core/model/`, the `youtrackBaseUrl` → `trackerBaseUrl` rename and its callers/tests, URL plumbing through `WorktreeCardService`, `PrDisplayData` view model in `iw.dashboard.presentation.views`, staleness mapping in `WorktreeListSync.scala:136`/`:224`, renderer additions (repo-link section + stale-PR badge), CSS, unit + integration tests.
  - Estimate: 3.25–7.25h
  - Rationale: Single self-contained PR; both card additions land together so reviewers see the full card change in one place. Comfortably above the floor.

**Total phases:** 2 (1 investigation + 1 implementation, for an aggregate 4.0–8.5 hours).

## Testing Strategy

### Per-Layer Testing

**Domain Layer:**
- Unit: table-driven tests for `RepoUrlBuilder.buildRepoUrl` covering: GitHub (`repository` set), GitLab on gitlab.com, GitLab self-hosted via `trackerBaseUrl`, GitLab nested groups (`group/subgroup/project`), Linear with `repository` set (renders), YouTrack with `repository` set (renders), and the "missing repository" None case.
- Rename coverage: ensure `core/test/ConfigFileTest.scala` exercises the `trackerBaseUrl` accessor; update the BATS test `test/config.bats:137` to use the new name.

**Application Layer:**
- Unit: tests for the staleness mapping in `WorktreeListSync.scala:136`/`:224` — fresh `CachedPR` produces `PrDisplayData(_, isStale=false)`, stale `CachedPR` produces `PrDisplayData(_, isStale=true)`, missing cache produces `None`.

**Presentation Layer:**
- Unit: renderer tests asserting:
  - Repo link rendered when `repoUrl = Some(...)`, absent when `None`.
  - PR section rendered when `prData = Some(...)` regardless of `isStale`.
  - `· stale` indicator on PR section iff `prData.get.isStale == true`.

**Frontend Layer:**
- Visual smoke check via the dev server (no automated tests typical for CSS-only changes).

**Test Data Strategy:**
- Reuse existing `ProjectConfiguration` and `PullRequestData` fixtures.
- Add minimal builders for the four tracker types if not already present.

**Regression Coverage:**
- Existing renderer tests must continue to pass after the `prData` parameter type change — update fixtures to wrap `PullRequestData` in `PrDisplayData(pr, isStale=false)` where they currently pass it bare.
- Card refresh polling cadence must be unaffected.

## Deployment Considerations

### Database Changes
None.

### Configuration Changes
No HOCON file-format change. The on-disk key remains `tracker.baseUrl`. The Scala accessor on `ProjectConfiguration` is renamed `youtrackBaseUrl` → `trackerBaseUrl`; the CLI alias `iw config get youtrackBaseUrl` is removed (no backward-compat). Existing user config files continue to work without modification.

### Rollout Strategy
Single PR per phase. No feature flag needed — both changes are purely additive UI/behavioural improvements.

### Rollback Plan
Standard `git revert` of each phase's PR. No data migration to undo.

## Dependencies

### Prerequisites
None — all referenced infrastructure (`ProjectConfiguration`, `PullRequestCacheService`, `WorktreeCardRenderer`) already exists.

### Layer Dependencies
- Phase 1 (investigation): no code dependencies; pure discovery.
- Phase 2 (implementation): Domain (`RepoUrlBuilder` + rename) → Application/Infra plumbing (URL + `PrDisplayData` mapping) → Presentation (renderer changes) → Frontend (CSS) → Tests.
- Phase 1's deliverable does not block Phase 2 — they can land in either order — but running the investigation first is recommended so any cache-architecture insight has a chance to inform Phase 2's tests or scope.

### External Blockers
None.

## Risks & Mitigations

### Risk 1: Cache investigation surfaces a problem larger than the parent-issue write-up can capture
**Likelihood:** Medium
**Impact:** Low (only delays opening the follow-up issue, doesn't block IW-347)
**Mitigation:** Time-box investigation to 1.25h. If the architecture is more entangled than expected, the write-up flags open questions and the follow-up issue's analysis phase explores them in depth.

### Risk 2: `trackerBaseUrl` rename misses a caller and breaks the build
**Likelihood:** Low (rename is mechanical; compiler catches any direct reference; touch radius is bounded — 6 Scala source files + 2 test files + 2 generated docs).
**Impact:** Low (compile-time failure, immediate feedback)
**Mitigation:** Run `scala-cli compile --scalac-option -Werror core/` and `./mill dashboard.test` after the rename; regenerate `docs/api/Config.md` and `docs/api/YouTrackClient.md`.

### Risk 3: GitLab self-hosted user has `tracker.baseUrl` set but it points to their YouTrack, not their GitLab
**Likelihood:** Low (current code already overloads this field for GitLab tracker URLs, so any GitLab-on-self-hosted user already has it set to the GitLab base URL)
**Impact:** Low (worst case: repo link points to wrong host; user sees broken link, no data corruption)
**Mitigation:** Mirror `TrackerUrlBuilder` behaviour exactly. Document in code comment.

### Risk 4: Renderer parameter type change (`prData: Option[PullRequestData]` → `Option[PrDisplayData]`) breaks existing callers
**Likelihood:** Medium (multiple call sites in `WorktreeListView`, `WorktreeListSync`, `ProjectDetailsView`, `WorktreeDetailView`, plus tests)
**Impact:** Low (compile-time failure, all sites must be updated)
**Mitigation:** Update all callers in a single change; default callers that don't have a `CachedPR` to `PrDisplayData(pr, isStale = false)`.

---

## Implementation Sequence

**Phase 1 (investigation):** standalone; no implementation order to manage.

**Phase 2 (implementation) — Recommended Layer Order:**

1. **Domain Layer** — `RepoUrlBuilder` + the `youtrackBaseUrl` → `trackerBaseUrl` rename in one go (rename is a prerequisite for clean references in the new builder).
2. **Application/Infrastructure Layer** — `PrDisplayData` view model + staleness mapping in `WorktreeListSync`; URL plumbing in `WorktreeCardService`.
3. **Presentation Layer** — `renderCard` parameter changes (new `repoUrl`, `prData` type change), repo-link section, stale-PR badge.
4. **Frontend Layer** — CSS for the new elements.
5. **Tests** — written alongside each layer (TDD), not after.

**Ordering Rationale:**
- Domain types must exist before anything can use them.
- Renderer parameter additions require call sites already updated, so application layer comes before presentation in the implementation sequence (even though presentation is "above" application architecturally).
- Frontend CSS comes last — needs the rendered HTML to style against.

## Documentation Requirements

- [ ] Code documentation: PURPOSE comments on `RepoUrlBuilder`, scaladoc on new public methods, brief comment at the staleness-preservation site explaining why the boolean is threaded.
- [ ] API documentation: not applicable (no external API change).
- [ ] Architecture decision record: not required — small additive change.
- [ ] User-facing documentation: optional release note ("Worktree cards now link to the git repo and show stale PR badges").
- [ ] Migration guide: not applicable.

---

**Analysis Status:** Clarifications resolved — ready for task generation.

**Next Steps:**
1. Run **wf-create-tasks** with IW-347.
2. Run **wf-implement** for the investigation phase first, then the implementation phase.
3. After Phase 1 (investigation) lands its write-up, open the follow-up parent issue for the cache-architecture rework.
