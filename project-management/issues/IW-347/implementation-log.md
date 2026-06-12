# Implementation Log: Dashboard — Add git repo web link and ensure PR link is always visible

Issue: IW-347

This log tracks the evolution of implementation across phases.

---

## Phase 1: Cache-architecture investigation (2026-04-29)

**Layer:** Discovery / Documentation (no code changes)

**What was built:**
- `project-management/issues/IW-347/cache-investigation.md` — structured write-up of the dashboard cache architecture (services inventory, write paths, read paths, refresh triggers, reproduction notes for symptoms A/B/C, open questions).
- `project-management/issues/IW-347/cache-rework-issue-draft.md` — draft issue body for opening a follow-up parent issue covering the cache-architecture rework.

**Key findings (diverged from analysis hypothesis):**
- "Caches are dashboard-only" is true for **writes** but not **reads**. The CLI silently consumes cache state via `iw status` (through `/api/v1/worktrees/:issueId/status`) and `iw worktrees` (through `StateReader` reading `state.json` directly). Neither has a fallback when the cache is empty.
- Symptom B is HTMX trigger-schedule overhead, not a synchronous fetch. Initial render serves a skeleton with `every 30s` (no `load` trigger), so the first poll fires +30 s after page load.
- Symptom C's deeper cause: `RefreshThrottle.recordRefresh` is gated only on a successful issue fetch (`WorktreeCardService.scala:108`). When tracker auth fails, the throttle never closes; transient `gh pr view` failures then produce a render with `prData = None` → PR section disappears.
- `RefreshThrottle` (30 s) and `CacheConfig` TTLs (15–30 min) are overlapping gates; the throttle dominates in practice and the TTL rarely fires.
- `ServerDaemon` does no background work (no scheduler, no warm-up). `ServerStateService`'s shape supports it cleanly.

**Dependencies on other layers:**
- None (first phase, discovery only).

**Testing:**
- Unit tests: 0 (none required for documentation-only phase).
- Integration tests: 0 (none required).
- Reproduction approach: code-path analysis with file/line citations, not live reproduction (per phase context guidance — dashboard is heavyweight to spin up).

**Code review:**
- Iterations: 0 (formal code-review skills do not apply to markdown documentation; sanity-checked manually).
- Review file: none.

**Files changed:**
```
A  project-management/issues/IW-347/cache-investigation.md
A  project-management/issues/IW-347/cache-rework-issue-draft.md
M  project-management/issues/IW-347/phase-01-tasks.md (checkboxes)
```

**For next phases:**
- Phase 2 (renderer-side mitigation for symptom C) can proceed independently. Phase 1's investigation may inform Phase 2's tests but is not blocking.
- The cache-rework-issue-draft.md is ready to be opened as a follow-up parent issue when desired; that work is explicitly out of scope for IW-347.

---

## Phase 2: Repo link + stale-PR badge (2026-04-30)

**Layer:** Domain + Application/Infrastructure + Presentation + Frontend (vertical slice)

**What was built:**
- `core/model/RepoUrlBuilder.scala` (new) - pure URL derivation from `ProjectConfiguration`. Returns `None` when `repository` is unset; emits a GitHub-style URL for GitHub/Linear/YouTrack and a `trackerBaseUrl`-or-`gitlab.com` URL for GitLab. Output filtered through an `http`/`https` scheme allow-list as defence in depth.
- `core/model/Config.scala` - renamed `youtrackBaseUrl` → `trackerBaseUrl` accessor, factory parameter, and HOCON serialisation/parsing sites (HOCON file key `tracker.baseUrl` unchanged). Added parse-time validation rejecting `trackerBaseUrl` without `http(s)://` and rejecting `repository` segments containing characters outside `[A-Za-z0-9._-]+` (and explicitly rejecting `.` and `..` segments to prevent path traversal).
- `core/model/TrackerUrlBuilder.scala` - reads `config.trackerBaseUrl` after the rename.
- `dashboard/jvm/src/presentation/views/PrDisplayData.scala` (new) - view model `PrDisplayData(pr, isStale)` with a `fromCached(cached, now)` factory that derives `isStale` from `CachedPR.isStale`. Replaces six previously inline `!CachedPR.isValid(_, now)` expressions.
- `dashboard/jvm/src/presentation/views/WorktreeCardRenderer.scala` - `renderCard` signature changed from `prData: Option[PullRequestData]` to `prData: Option[PrDisplayData]` and gained `repoUrl: Option[String] = None`. Renders a new repo-link section (`<p class="repo-link"><a class="repo-button" href rel="noopener noreferrer" target="_blank">Repo</a></p>`) immediately after the issue-id badge when `repoUrl` is defined; appends `<span class="stale-indicator"> · stale</span>` inside the existing PR section when `prData.get.isStale == true`. The PR section is no longer dropped when stale.
- `dashboard/jvm/src/WorktreeListSync.scala` - `generateAdditionOob`, `generateReorderOob`, and `generateChangesResponse` now thread `repoUrl: Option[String]` (or `repoUrlFor: String => Option[String]` lookup) through to the renderer.
- `dashboard/jvm/src/WorktreeCardService.scala` - converts `Option[CachedPR]` to `Option[PrDisplayData]` via the new factory at all four cache-fallback branches.
- `dashboard/jvm/src/CaskServer.scala` - new private `repoUrlForWorktree(worktrees)` lookup helper; reads each worktree's `.iw/config.conf` and returns the repo URL for use by both the project-details view and the OOB-changes endpoints. Plumbed into both the per-worktree render path and the two `/api/worktrees/changes` routes.
- `dashboard/jvm/resources/static/dashboard.css` - added `.repo-link` container rule (mirrors `.pr-link`) and `.repo-button`/`.repo-button:hover` rules (sibling treatment to `.pr-button`).
- `commands/config.scala`, `commands/issue.scala`, `commands/init.scala` - rename sweep across the CLI alias map, help text, and identifiers; CLI alias `youtrackBaseUrl` removed (no backward-compat alias per CLARIFY 2). Help-text wording changed from "Aliases for backward compatibility:" to "Short-form field aliases:" to keep user-facing strings evergreen.
- `test/config.bats` - renamed BATS test "config get youtrackBaseUrl when unset returns error" to use `trackerBaseUrl`.
- `docs/api/Config.md`, `docs/api/YouTrackClient.md` - regenerated identifier references to `trackerBaseUrl`.

**Dependencies on other layers:**
- Domain: consumed by application/infra (`RepoUrlBuilder`, renamed `trackerBaseUrl`).
- Application/Infra: produces `PrDisplayData` for the renderer; reads per-worktree config to compute `repoUrl` on the request path.
- Presentation: consumes `Option[PrDisplayData]` and `Option[String]` repo URL.
- Frontend: consumes class names emitted by the renderer; `.stale-indicator` rule is reused verbatim from the existing issue-cache-staleness path.

**Testing:**
- Unit (Scala): `core/test/RepoUrlBuilderTest.scala` (new, 12 cases covering all four `IssueTrackerType` values, GitLab self-hosted with/without trailing slash, nested groups, the `repository = None` case, and the unsafe-scheme defence-in-depth filter); `core/test/ConfigTest.scala` (added cases asserting rejection of unsafe `repository` segments and unsafe `trackerBaseUrl` schemes); updates to `core/test/ConfigFileTest.scala`, `core/test/TestFixtures.scala`, `core/test/TrackerUrlBuilderTest.scala` for the rename.
- Unit (Dashboard): `WorktreeCardRendererTest` (added cases for repo-link rendering with/without `repoUrl`, PR section rendered iff `prData.isDefined`, stale badge present iff `prData.get.isStale == true`, plus a `prSection` extractor to disambiguate the assertion from the card-level stale indicator); `WorktreeListSyncTest` (new staleness coverage at both `generateAdditionOob` and `generateReorderOob` for fresh / expired / missing PR cache, plus updates to all existing call sites for the new positional argument); `WorktreeCardServiceTest`, `ProjectDetailsViewTest`, `IssueSearchServiceTest` updated for the rename and renderer signature change.
- Integration (Mill): `dashboard.itest.testForked` 192/192 green.
- BATS E2E: 422/422 of the test files touched by this phase pass; 9 unrelated failures in pre-existing flaky tests (`open.bats`, `start.bats`, `phase-merge.bats`, `dashboard-dev-mode.bats`) that this phase does not modify.

**Code review:**
- Iterations: 2 (initial 6-skill parallel review, then focused security re-review)
- Initial review identified 2 critical security issues: `javascript:` URL injection via `trackerBaseUrl` in the repo-link `href`, and weak `repository` validation enabling open-redirect / path-traversal. Both fixed at parse time (input sanitisation in `Config.scala`) and at output time (scheme filter in `RepoUrlBuilder`), with explicit tests for each. Iteration-2 re-review confirmed both critical issues resolved.
- Initial review also flagged a real functional gap: `WorktreeListSync.generateAdditionOob` and `generateReorderOob` silently dropped `repoUrl` (the `= None` default kicked in), making the repo link disappear from cards inserted/reordered via HTMX OOB swaps. Fixed by threading `repoUrl: Option[String]` through both generators and `generateChangesResponse(repoUrlFor: String => Option[String])`.
- Other warnings addressed inline: replaced six `!CachedPR.isValid(_, now)` sites with a `PrDisplayData.fromCached(_, now)` factory; made `IssueTrackerType` match exhaustive in `RepoUrlBuilder`; added `rel="noopener noreferrer"` on the new repo link; added the missing `.repo-link` CSS rule; renamed the user-visible help string "Aliases for backward compatibility:" → "Short-form field aliases:" to keep copy evergreen.
- Review files: `review-phase-02-20260430-142658.md`

**Files changed:** 25 files, 699 insertions(+), 126 deletions(-) (excluding workflow/state files).

**For next phases:** Phase 2 is the only implementation phase for IW-347; the issue's two remaining follow-ups are:
1. Cache-architecture rework (CLARIFY 3 in analysis, draft in `cache-rework-issue-draft.md`).
2. `renderCard` parameter count is now 12 — a future ticket may introduce a `RenderCardContext` case class to bundle per-card data; deferred from this phase as out of scope.

---
