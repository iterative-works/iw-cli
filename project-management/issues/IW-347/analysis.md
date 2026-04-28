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

Add a pure `RepoUrlBuilder` in `core/model/` mirroring the shape of the existing `TrackerUrlBuilder`, derive the URL from `ProjectConfiguration` (and optionally the current branch from `GitStatus`), and plumb it through to `WorktreeCardRenderer` as a new optional parameter rendered next to the existing issue-id link.

For the PR-link visibility, first **diagnose the root cause** before changing behaviour. The current code already returns stale cache entries via `PullRequestCacheService.getCachedOnly`, so the most likely cause is that on initial render after server start no PR has been fetched yet. The minimal fix is to ensure (a) any cached PR data — fresh or stale — flows through to the renderer, (b) staleness is preserved as a flag rather than being dropped at the `prData.map(_.pr)` boundary in `WorktreeListSync.scala`, and (c) a background fetch is kicked off on first cache miss so the next refresh has data. The renderer always renders the PR section when data exists and shows a `· stale` badge mirroring the existing `stale-indicator` convention.

### Why This Approach

- Mirroring `TrackerUrlBuilder` keeps URL construction pure, testable, and consistent with existing conventions.
- Threading staleness through as a small value object (e.g. `PrDisplayData(pr, isStale)`) instead of overloading `PullRequestData` keeps the domain model unchanged for callers that don't care about display concerns.
- Pushing the cache-timing fix toward "always show last known data" rather than "fetch synchronously on render" keeps render paths fast and matches the existing fall-back-to-stale pattern.

## Architecture Design

### Domain Layer (`core/model/`)

**Components:**
- `RepoUrlBuilder` — pure object with `buildRepoUrl(config: ProjectConfiguration, branch: Option[String]): Option[String]`, mirroring `TrackerUrlBuilder` shape.
- Optional small value object (e.g. `PrDisplayData(pr: PullRequestData, isStale: Boolean)`) — see CLARIFY 4 for the alternative of adding a parameter to the renderer instead.

**Responsibilities:**
- Construct repo web URLs for GitHub and GitLab from `ProjectConfiguration.repository` and (for self-hosted GitLab) `youtrackBaseUrl`.
- Decide URL target shape (branch page vs. repo root vs. compare view) — see CLARIFY 1.
- Return `None` when configuration is insufficient (e.g. `repository` not set, or tracker type is one we don't know how to map).

**Estimated Effort:** 0.5–1.5h
**Complexity:** Straightforward

---

### Application/Infrastructure Layer (`dashboard/jvm/src/`)

**Components:**
- Update `WorktreeCardService` (or wherever card data is composed) to compute the repo URL via `RepoUrlBuilder` and forward it to the renderer.
- Update `WorktreeListSync.scala` lines 136 and 224 (the two call sites that currently do `prData.map(_.pr)`) to forward staleness, either as `PrDisplayData` or as a parallel `Boolean` parameter.
- Investigate the cache-timing root cause (CLARIFY 3) and apply the minimal fix — most likely a "fire-and-forget background fetch on first miss" in the cache-only render path.

**Responsibilities:**
- Orchestrate config + git status + cached PR into the data the renderer needs.
- Preserve staleness signal end-to-end instead of dropping it on the way to view code.
- Trigger PR fetches such that the cache is populated by the next refresh tick.

**Estimated Effort:** 1.5–3h
**Complexity:** Moderate (range driven by cache-timing investigation; see CLARIFY 3)

---

### Presentation Layer (`dashboard/jvm/src/presentation/views/`)

**Components:**
- New "repo link" section in `WorktreeCardRenderer.renderCard`, rendered near the issue-id link.
- New optional `repoUrl: Option[String]` parameter on `renderCard`.
- New optional staleness signal for PR data — either `prData: Option[PrDisplayData]` or an additional `prIsStale: Boolean` parameter.
- Stale badge on the PR section reusing the `stale-indicator` text/CSS pattern that already exists for issue-data staleness (line 215–220 of the renderer).

**Responsibilities:**
- Render the repo link as a persistent button when URL is available; do nothing when None.
- Always render the PR section when PR data is present (already true) and add a `· stale` indicator when the cache entry is past its TTL.

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
- Unit tests for `RepoUrlBuilder` — table-driven across all four `IssueTrackerType` values plus the "no repository configured" case.
- Renderer tests asserting the repo link is present when URL is provided and absent when not, and the stale-PR badge appears only when `isStale` is true.
- Integration test for the cache-timing fix — exercise the path where the first render finds an empty cache and confirm a subsequent render has PR data.

**Estimated Effort:** 0.5–1.5h
**Complexity:** Straightforward (per-layer unit tests are easy; the integration test depends on the cache-timing fix shape — see CLARIFY 3)

---

## Technical Decisions

### Patterns

- Pure URL builder mirroring `TrackerUrlBuilder` — keeps domain layer free of I/O.
- Functional core / imperative shell — URL derivation in `model/`, composition in `dashboard/`.
- Optional value object (`PrDisplayData`) over flag-parameter sprawl — but flagged under CLARIFY 4 for explicit decision.

### Technology Choices

- **Frameworks/Libraries:** No new dependencies. Scalatags for the new card section, existing CSS for styling.
- **Data Storage:** None — config already provides `repository`; cache plumbing is already in place.
- **External Systems:** None new. Repo URLs are static derivations; no I/O is required to compute them.

### Integration Points

- `RepoUrlBuilder` consumes `ProjectConfiguration` (already loaded by dashboard at startup) and optionally a branch name from `GitStatus`.
- `WorktreeCardService`/`WorktreeListSync` thread the URL and PR display data into `WorktreeCardRenderer.renderCard`.
- The cache-timing fix lives entirely on the dashboard side; no new external integrations.

## Technical Risks & Uncertainties

### CLARIFY: Repo URL target shape per tracker type

For GitHub and GitLab the `repository` field gives `owner/repo`, but the link could point to several useful targets.

**Questions to answer:**
1. Should the link go to the branch page (`/tree/{branch}`), the repo root, or the compare-to-main view (`/compare/main...{branch}`)?
2. Should we render the repo link when `trackerType` is Linear or YouTrack but `repository` is set (i.e. tracker decoupled from code host)?
3. Should the link auto-fall-back to repo root when the branch is unknown (no `gitStatus`)?

**Options:**
- **Option A — Branch page when known, repo root otherwise:** most informative; degrades cleanly. Requires reading `gitStatus.branchName` (already in the renderer signature). Branch URL: `https://github.com/{owner}/{repo}/tree/{branch}`.
- **Option B — Always repo root:** simplest; URL doesn't change per worktree. Loses the "open this branch on GitHub" affordance.
- **Option C — Compare-to-main when no PR exists, branch page when PR exists, repo root as fallback:** richest UX; more branching logic in the builder.

**Impact:** Affects `RepoUrlBuilder` signature (does it take a branch or not?), the renderer call site, and test coverage.

---

### CLARIFY: Source of git host base URL for self-hosted GitLab

`TrackerUrlBuilder` reuses `youtrackBaseUrl` as the GitLab base URL — a known overload of a misnamed field.

**Questions to answer:**
1. Should `RepoUrlBuilder` mirror that overload for consistency, or introduce a dedicated `gitlabBaseUrl` config field?
2. Is there appetite to rename `youtrackBaseUrl` to a tracker-agnostic name in this issue, or defer to a follow-up?

**Options:**
- **Option A — Mirror the existing overload:** zero config-schema change, consistent with current code.
- **Option B — Add a new `repoBaseUrl` field, fall back to `youtrackBaseUrl` if unset:** cleaner naming, no breakage.
- **Option C — Read from `git remote get-url origin`:** requires I/O (adapter) — breaks the "pure builder" property and adds failure modes for offline/missing remote.

**Impact:** Layer purity, config schema, follow-up cleanup work.

---

### CLARIFY: Root cause of "missing PR link due to cache timing"

The brief gives two hypotheses; resolving this changes the application-layer fix shape and effort.

**Questions to answer:**
1. Is there a reproduction case for "PR exists but link is missing" after the cache should already be populated?
2. Does any code path actively evict cached PR entries (search for `.remove(`, `.invalidate(`, etc. on the PR cache)?
3. Should the dashboard kick a background PR fetch on first cache miss, or is the periodic refresh tick sufficient?

**Options:**
- **Option A — First-render miss is the only cause; trigger a background fetch on cache miss:** small change in `WorktreeCardService` or wherever the cache-only path runs.
- **Option B — Active eviction is removing entries; remove or fix the eviction:** different, smaller change in cache code, no fetch triggering needed.
- **Option C — Both:** combine A and B if both contribute.

**Impact:** Application-layer effort range (1.5h vs. 3h), shape of integration test, and whether any timing-related tests need adjustment.

---

### CLARIFY: How to thread PR staleness to the renderer

The renderer currently has `prData: Option[PullRequestData]` and `isStale` only refers to issue data. We need PR-staleness too.

**Questions to answer:**
1. Add a `PrDisplayData(pr, isStale)` value object in `core/model/`, or pass `prIsStale: Boolean` alongside `prData`?
2. Does the value-object approach pollute the domain with display concerns?

**Options:**
- **Option A — `PrDisplayData` value object in `core/model/`:** explicit, self-describing, easy to extend if other display flags accrue. Minor domain pollution.
- **Option B — Parallel `Boolean` parameter on `renderCard`:** smallest change, parameter-list grows but renderer already has 11 params.
- **Option C — Reuse `CachedPR` directly:** the renderer would compute staleness itself from `fetchedAt`, but this leaks cache concerns into presentation.

**Impact:** Where the staleness check lives, renderer signature, and whether this introduces a new domain type.

---

### CLARIFY: Branch-name source for repo URL

`WorktreeRegistration` does not carry a branch name (verified — it has `issueId`, `path`, `trackerType`, `team`, timestamps). The renderer already receives `gitStatus: Option[GitStatus]` which has `branchName`.

**Questions to answer:**
1. Confirm the renderer should source branch from `gitStatus.branchName` when present, falling back to repo root URL when `gitStatus` is None.
2. Should `RepoUrlBuilder.buildRepoUrl` take `branch: Option[String]` or two separate methods (`buildRepoRootUrl`, `buildBranchUrl`)?

**Options:**
- **Option A — Single `buildRepoUrl(config, branch: Option[String])`:** caller passes `gitStatus.map(_.branchName)`, builder picks the right URL.
- **Option B — Two methods, caller picks:** simpler builder per-method, slightly more logic in caller.

**Impact:** Builder API shape, test table structure.

---

## Total Estimates

**Per-Layer Breakdown:**
- Domain Layer: 0.5–1.5h
- Application/Infrastructure Layer: 1.5–3h
- Presentation Layer: 0.5–1.5h
- Frontend Layer: 0.25–0.75h
- Testing Layer: 0.5–1.5h

**Total Range:** 3.25 – 8.25 hours

**Confidence:** Medium

**Reasoning:**
- Domain and presentation work is mechanical and well-scoped.
- Application range is wide because it depends on the cache-timing root cause (CLARIFY 3) — could be a one-line background-fetch trigger or a slightly larger eviction fix.
- Testing range is wide because the integration test for the cache fix depends on the fix shape.
- No new dependencies, no schema migrations, no protocol changes.

## Recommended Phase Plan

Total mid-point ~5.7h, low-end 3.25h. With the 3h phase-size floor, no individual layer except the application layer reaches the floor on its own, so we merge into two phases that respect dependency order (domain → infra → application → presentation, with frontend and tests folded into the phase that exercises them).

- **Phase 1: Repo link end-to-end (Domain + Presentation + Frontend + tests for repo link)**
  - Includes: `RepoUrlBuilder` in `core/model/`, plumbing through `WorktreeCardService`/`WorktreeListSync`, renderer addition, CSS, unit tests for builder and renderer.
  - Estimate: 1.75–4.25h
  - Rationale: Pure-additive vertical slice for the new repo link. Self-contained, easy to review as one PR. Below the 3h floor only at the very low end — acceptable because merging it with Phase 2 would create a too-large PR mixing unrelated concerns (URL building vs. cache investigation).

- **Phase 2: PR link visibility fix (Application/Infra + staleness plumbing + integration test)**
  - Includes: cache-timing investigation and fix, `PrDisplayData` (or boolean flag) plumbed through `WorktreeListSync.scala:136` and `:224`, renderer stale-PR badge, integration test.
  - Estimate: 2.5–5h (excluding the renderer addition for the stale badge if Phase 1 already lands the parameter; if not, add ~0.5h)
  - Rationale: Distinct concern (data-flow correctness vs. UI addition). Investigation-heavy, benefits from its own review focus. Comfortably above the floor.

**Total phases:** 2 (for total estimate 3.25–8.25 hours)

Note: if CLARIFY 3 resolves to "active eviction bug" (Option B) the application work shrinks substantially; in that case consider folding everything into a single phase. Defer that decision until investigation is done.

## Testing Strategy

### Per-Layer Testing

**Domain Layer:**
- Unit: table-driven tests for `RepoUrlBuilder.buildRepoUrl` covering GitHub, GitLab (gitlab.com), GitLab (self-hosted via `youtrackBaseUrl`), Linear (no repo or repo set — verify CLARIFY 1 outcome), YouTrack (same), and the "missing repository" None case.
- Branch-aware variants: with and without a branch, verifying the chosen URL shape from CLARIFY 1.

**Application Layer:**
- Unit: tests for the staleness-preservation change at `WorktreeListSync.scala:136`/`:224`.
- Integration: cache-miss-then-refresh scenario for the PR-link fix.

**Presentation Layer:**
- Unit: renderer tests asserting:
  - Repo link rendered when `repoUrl = Some(...)`, absent when `None`.
  - PR section rendered when `prData = Some(...)` regardless of staleness.
  - `· stale` indicator on PR section iff `isStale = true`.

**Frontend Layer:**
- Visual smoke check via the dev server (no automated tests typical for CSS-only changes).

**Test Data Strategy:**
- Reuse existing `ProjectConfiguration` and `PullRequestData` fixtures.
- Add minimal builders for the four tracker types if not already present.

**Regression Coverage:**
- Existing renderer tests must continue to pass (PR section already optional; no behavioural change for callers that don't pass a repo URL).
- Card refresh polling cadence must be unaffected.

## Deployment Considerations

### Database Changes
None.

### Configuration Changes
None required. Existing `repository` and `youtrackBaseUrl` fields are reused. If CLARIFY 2 picks Option B (new field), that becomes additive and optional with safe fall-back.

### Rollout Strategy
Single PR per phase. No feature flag needed — both changes are purely additive UI/behavioural improvements.

### Rollback Plan
Standard `git revert` of each phase's PR. No data migration to undo.

## Dependencies

### Prerequisites
None — all referenced infrastructure (`ProjectConfiguration`, `PullRequestCacheService`, `WorktreeCardRenderer`) already exists.

### Layer Dependencies
- Phase 1: Domain → Application/Infra plumbing → Presentation → Frontend (sequential within the phase).
- Phase 2: Investigation → Application/Infra fix → Presentation (stale badge) → Tests.
- Phases are independent and could be parallelised, but landing Phase 1 first is recommended because it's lower-risk.

### External Blockers
None.

## Risks & Mitigations

### Risk 1: Cache-timing root cause is more complex than expected
**Likelihood:** Medium
**Impact:** Medium
**Mitigation:** Time-box investigation to 1h. If root cause isn't clear, surface to Michal with hypothesis and reproduction steps before continuing.

### Risk 2: GitLab self-hosted users with `youtrackBaseUrl` overloaded for tracker, not repo host
**Likelihood:** Low (current code already overloads this for GitLab tracker URLs, so any user who has it set is already implicitly using it for GitLab)
**Impact:** Low (worst case: repo link points to wrong host; user sees broken link, no data corruption)
**Mitigation:** Mirror `TrackerUrlBuilder` behaviour exactly. Document in code comment. Surface the rename question separately (CLARIFY 2).

### Risk 3: `PrDisplayData` value object pollutes domain layer
**Likelihood:** Low
**Impact:** Low
**Mitigation:** If concerned, prefer Option B in CLARIFY 4 (parallel boolean parameter) — keeps the domain pristine at the cost of one more renderer parameter.

---

## Implementation Sequence

**Recommended Layer Order (within each phase):**

1. **Domain Layer** — `RepoUrlBuilder` first; pure logic with no dependencies; foundation for all other layers.
2. **Application/Infrastructure Layer** — wire the builder into card composition; investigate and fix cache timing.
3. **Presentation Layer** — add renderer parameters and the new card sections.
4. **Frontend Layer** — CSS to make the new elements look right.
5. **Tests** — written alongside each layer (TDD), not after.

**Ordering Rationale:**
- Domain types must exist before anything can use them.
- Renderer parameter additions require call sites already updated, so application layer comes before presentation in the implementation sequence (even though presentation is "above" application architecturally).
- Frontend CSS comes last — needs the rendered HTML to style against.
- Phase 1 (repo link) is purely additive and lower-risk; recommend implementing before Phase 2 (cache-timing investigation).

## Documentation Requirements

- [ ] Code documentation: PURPOSE comments on `RepoUrlBuilder`, scaladoc on new public methods, brief comment at the staleness-preservation site explaining why the boolean is threaded.
- [ ] API documentation: not applicable (no external API change).
- [ ] Architecture decision record: not required — small additive change.
- [ ] User-facing documentation: optional release note ("Worktree cards now link to the git repo and show stale PR badges").
- [ ] Migration guide: not applicable.

---

**Analysis Status:** Ready for Review

**Next Steps:**
1. Resolve CLARIFY markers with Michal (especially CLARIFY 1, 3, 4 — the others have safe defaults).
2. Run **wf-create-tasks** with IW-347.
3. Run **wf-implement** for layer-by-layer implementation.
