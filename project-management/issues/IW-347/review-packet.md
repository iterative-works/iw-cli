---
generated_from: d7803a16ad2a75ae4c0bafbe37bb57aa2c94486f
generated_at: 2026-05-29T07:26:53Z
branch: IW-347
issue_id: IW-347
phase: 2
files_analyzed:
  - core/model/RepoUrlBuilder.scala
  - core/model/Config.scala
  - core/model/TrackerUrlBuilder.scala
  - dashboard/jvm/src/presentation/views/PrDisplayData.scala
  - dashboard/jvm/src/presentation/views/WorktreeCardRenderer.scala
  - dashboard/jvm/src/presentation/views/ProjectDetailsView.scala
  - dashboard/jvm/src/WorktreeListSync.scala
  - dashboard/jvm/src/WorktreeCardService.scala
  - dashboard/jvm/src/CaskServer.scala
  - dashboard/jvm/src/DashboardService.scala
  - dashboard/jvm/src/IssueSearchService.scala
  - dashboard/jvm/src/WorktreeListView.scala
  - dashboard/jvm/resources/static/dashboard.css
  - commands/config.scala
  - commands/issue.scala
  - commands/init.scala
  - core/test/RepoUrlBuilderTest.scala
  - core/test/ConfigTest.scala
  - core/test/ConfigFileTest.scala
  - core/test/TestFixtures.scala
  - core/test/TrackerUrlBuilderTest.scala
  - dashboard/jvm/test/src/WorktreeCardRendererTest.scala
  - dashboard/jvm/test/src/WorktreeListSyncTest.scala
  - dashboard/jvm/test/src/WorktreeCardServiceTest.scala (updated call sites)
  - dashboard/jvm/test/src/ProjectDetailsViewTest.scala
  - dashboard/jvm/test/src/IssueSearchServiceTest.scala
  - test/config.bats
  - docs/api/Config.md
  - docs/api/YouTrackClient.md
---

# Review Packet: IW-347 — Dashboard: Repo Link + Stale-PR Badge

## Goals

This branch adds two persistent UI signals to the worktree card and performs a
long-overdue field rename to clean up technical debt.

Key objectives:

- **Repo web link.** Add a "Repo" button to every worktree card whenever
  `ProjectConfiguration.repository` is set. The button links to the git host
  page (GitHub or GitLab, including self-hosted) and is always visible — not
  dependent on PR or issue cache state.
- **Stale-PR badge.** Stop the PR link from disappearing when the cache becomes
  stale. When the renderer receives PR data it always renders the PR section;
  when that data has exceeded its TTL the section gets an additional
  `· stale` indicator mirroring the existing issue-staleness pattern.
- **`youtrackBaseUrl` → `trackerBaseUrl` rename.** Replace a YouTrack-centric
  field name with a tracker-agnostic one. The on-disk HOCON key
  (`tracker.baseUrl`) is unchanged; only the Scala accessor, factory
  parameter, and CLI alias are renamed.
- **No cache or fetch policy changes.** The deeper cache-architecture problem
  (dashboard-only warm-up, CLI blind spots, first-visit 30 s lag) is
  deliberately deferred to a separate follow-up issue, documented in
  `cache-rework-issue-draft.md`.

## Scenarios

- [ ] A card with `repository` configured shows a "Repo" button next to the
      issue-id badge.
- [ ] A card without `repository` configured shows no repo-link section.
- [ ] A card with a fresh cached PR still shows the full PR section (no
      regression).
- [ ] A card with a stale cached PR shows the PR section _and_ a `· stale`
      indicator inside it.
- [ ] A card with no PR data shows no PR section (no regression).
- [ ] `iw config get trackerBaseUrl` resolves through the CLI alias map to
      `tracker.baseUrl` in the HOCON config.
- [ ] `iw config get youtrackBaseUrl` no longer works (alias removed, no
      backward-compat shim).
- [ ] Existing user config files with `tracker.baseUrl` continue to load and
      round-trip without modification.
- [ ] GitLab self-hosted: the repo URL is derived from `trackerBaseUrl`, with
      `https://gitlab.com` as the fallback.
- [ ] GitLab nested groups (`group/subgroup/project`) produce the correct URL.
- [ ] Linear- and YouTrack-tracked projects with `repository` set produce a
      `https://github.com/{owner}/{repo}` URL.
- [ ] A `trackerBaseUrl` with a non-http(s) scheme (e.g., `javascript:`) is
      rejected at parse time and also filtered in `RepoUrlBuilder`.
- [ ] HTMX OOB swaps (card addition and reorder) include the repo link, not
      just the initial render.

## Entry Points

| File | Symbol | Why Start Here |
|------|--------|----------------|
| `core/model/RepoUrlBuilder.scala` | `RepoUrlBuilder.buildRepoUrl` | New pure function; the domain root of the repo-link feature |
| `core/model/Config.scala` | `ProjectConfiguration.trackerBaseUrl` | Renamed accessor; all downstream callers fan out from here |
| `dashboard/jvm/src/presentation/views/PrDisplayData.scala` | `PrDisplayData` / `fromCached` | New view model; the staleness signal enters the renderer through this type |
| `dashboard/jvm/src/presentation/views/WorktreeCardRenderer.scala` | `renderCard` | Updated signature (`prData: Option[PrDisplayData]`, `repoUrl: Option[String]`); the HTML for both new features lives here |
| `dashboard/jvm/src/WorktreeListSync.scala` | `generateAdditionOob` / `generateReorderOob` / `generateChangesResponse` | Application layer that maps `Option[CachedPR]` to `Option[PrDisplayData]` and threads `repoUrl` to the renderer; the code-review bug (OOB paths silently dropping `repoUrl`) was found and fixed here |
| `dashboard/jvm/src/WorktreeCardService.scala` | `renderCard` | Four cache-fallback branches now produce `PrDisplayData`; review the staleness derivation at each branch |
| `dashboard/jvm/src/CaskServer.scala` | `repoUrlForWorktree` | New private helper that reads per-worktree config and feeds `repoUrl` into both the project-details and OOB-changes endpoints |

## Diagrams

### Component Relationships (Phase 2 additions in bold)

```
core/model/
  ProjectConfiguration
    └─ trackerBaseUrl (renamed from youtrackBaseUrl)
    └─ repository
  TrackerUrlBuilder         (reads trackerBaseUrl — updated)
  **RepoUrlBuilder**        (NEW — reads repository + trackerBaseUrl)
  CachedPR / CachedPR.isStale

dashboard/jvm/src/
  CaskServer
    └─ **repoUrlForWorktree()**  (NEW helper)
    └─ calls WorktreeCardService.renderCard(repoUrl = ...)
    └─ calls generateChangesResponse(repoUrlFor = ...)

  WorktreeCardService
    └─ maps Option[CachedPR] → **Option[PrDisplayData]**
    └─ calls WorktreeCardRenderer.renderCard

  WorktreeListSync
    └─ generateAdditionOob  → renderCard(repoUrl, prData)
    └─ generateReorderOob   → renderCard(repoUrl, prData)
    └─ generateChangesResponse(repoUrlFor)

presentation/views/
  **PrDisplayData(pr, isStale)**  (NEW view model)
    └─ **PrDisplayData.fromCached(cached, now)**
  WorktreeCardRenderer.renderCard
    └─ prData: Option[PrDisplayData]   (was Option[PullRequestData])
    └─ repoUrl: Option[String] = None  (NEW parameter)
    └─ renders repo-link section
    └─ renders · stale badge in PR section

dashboard/jvm/resources/static/dashboard.css
  .repo-link  (NEW container, mirrors .pr-link)
  .repo-button / .repo-button:hover  (NEW, mirrors .pr-button)
  .stale-indicator  (REUSED verbatim for PR stale badge)
```

### Data Flow: Staleness Signal

```
CachedPR (in cache store)
    │
    │  WorktreeCardService / WorktreeListSync
    │  PrDisplayData.fromCached(cached, now)
    │  isStale = CachedPR.isStale(cached, now)
    ▼
PrDisplayData(pr, isStale)
    │
    │  WorktreeCardRenderer.renderCard
    │  prData: Option[PrDisplayData]
    ▼
HTML: div.pr-link
  ├─ a.pr-button  (always rendered when prData is defined)
  ├─ span.pr-badge
  └─ span.stale-indicator " · stale"  (only when isStale = true)
```

### Data Flow: Repo URL

```
ProjectConfiguration.repository  (e.g. "org/repo")
ProjectConfiguration.trackerBaseUrl  (optional, for GitLab self-hosted)
    │
    │  RepoUrlBuilder.buildRepoUrl(config)
    │  scheme allow-list filter
    ▼
Option[String]  (repoUrl)
    │
    │  CaskServer.repoUrlForWorktree  (per-request lookup)
    ▼
WorktreeCardRenderer.renderCard(repoUrl = Some/None)
    │
    ▼
HTML: p.repo-link > a.repo-button[href, target=_blank, rel=noopener]
```

## Test Summary

### Unit Tests — Core (scala-cli / munit)

| Test File | New Cases | What is Verified |
|-----------|-----------|-----------------|
| `core/test/RepoUrlBuilderTest.scala` | 12 (all new) | GitHub URL, GitLab gitlab.com, GitLab self-hosted with/without trailing slash, GitLab nested groups, Linear with repo set, YouTrack with repo set, `repository = None` returns `None`, unsafe scheme (`javascript:`) returns `None` |
| `core/test/ConfigTest.scala` | Added | Rejection of unsafe `repository` path segments; rejection of non-http(s) `trackerBaseUrl` |
| `core/test/ConfigFileTest.scala` | Updated | `trackerBaseUrl` accessor after rename |
| `core/test/TrackerUrlBuilderTest.scala` | Updated | Field rename; existing behaviour unchanged |
| `core/test/TestFixtures.scala` | Updated | Fixture uses `trackerBaseUrl` |

### Unit Tests — Dashboard (Mill / munit)

| Test File | New Cases | What is Verified |
|-----------|-----------|-----------------|
| `dashboard/jvm/test/src/WorktreeCardRendererTest.scala` | 5 new | Repo-link present when `repoUrl = Some(...)`, absent when `None`; PR section present with `isStale = false` (no stale indicator); PR section present with `isStale = true` (stale indicator present); PR section absent when `prData = None`; `extractPrSection` helper to isolate PR-level stale from card-level stale |
| `dashboard/jvm/test/src/WorktreeListSyncTest.scala` | Added staleness cases | `generateAdditionOob` and `generateReorderOob` receive `repoUrl` and `prData`; fresh/expired/missing `CachedPR` produces correct `PrDisplayData` |
| `dashboard/jvm/test/src/WorktreeCardServiceTest.scala` | Updated | Call sites updated for new `renderCard` signature |
| `dashboard/jvm/test/src/ProjectDetailsViewTest.scala` | Updated | `repoUrl` parameter propagation |
| `dashboard/jvm/test/src/IssueSearchServiceTest.scala` | Updated | `trackerBaseUrl` rename |

### E2E Tests (BATS)

| Test File | Change | What is Verified |
|-----------|--------|-----------------|
| `test/config.bats` | Renamed test | `iw config get trackerBaseUrl when unset returns error` (was `youtrackBaseUrl`); alias `trackerBaseUrl → tracker.baseUrl` resolves correctly |

### Reported Counts (from implementation log)

- Mill integration tests: 192/192 green (`dashboard.itest.testForked`)
- BATS E2E: 422/422 for files touched by this phase; 9 pre-existing failures in unrelated flaky tests (`open.bats`, `start.bats`, `phase-merge.bats`, `dashboard-dev-mode.bats`) not caused by this change

## Files Changed

39 files changed (25 implementation files + 14 workflow/PM files).

### New Files

| File | Description |
|------|-------------|
| `core/model/RepoUrlBuilder.scala` | Pure URL builder; derives repo root URL from `ProjectConfiguration` |
| `core/test/RepoUrlBuilderTest.scala` | 12 table-driven unit tests for `RepoUrlBuilder` |
| `dashboard/jvm/src/presentation/views/PrDisplayData.scala` | View model bundling `PullRequestData` with `isStale` flag; `fromCached` factory |

### Modified — Domain + Commands

| File | Change Summary |
|------|----------------|
| `core/model/Config.scala` | Rename `youtrackBaseUrl` → `trackerBaseUrl` accessor/parameter; parse-time validation for unsafe `trackerBaseUrl` scheme and invalid `repository` path segments |
| `core/model/TrackerUrlBuilder.scala` | Reads `config.trackerBaseUrl` after rename |
| `commands/config.scala` | CLI alias map: `youtrackBaseUrl` → `trackerBaseUrl`; help text updated to "Short-form field aliases:" |
| `commands/issue.scala` | Rename sweep |
| `commands/init.scala` | Rename sweep |

### Modified — Dashboard Application + Presentation

| File | Change Summary |
|------|-------------|
| `dashboard/jvm/src/presentation/views/WorktreeCardRenderer.scala` | New `repoUrl: Option[String]` param; `prData` type changed to `Option[PrDisplayData]`; repo-link section added; `· stale` badge in PR section |
| `dashboard/jvm/src/presentation/views/ProjectDetailsView.scala` | Threads `repoUrl` to `renderCard` |
| `dashboard/jvm/src/WorktreeListSync.scala` | Threads `repoUrl` and `Option[PrDisplayData]` through `generateAdditionOob`, `generateReorderOob`, `generateChangesResponse` |
| `dashboard/jvm/src/WorktreeCardService.scala` | Four cache-fallback branches produce `PrDisplayData` via `fromCached` |
| `dashboard/jvm/src/CaskServer.scala` | New `repoUrlForWorktree` helper; plumbs `repoUrl` into both card-render paths |
| `dashboard/jvm/src/DashboardService.scala` | Rename sweep |
| `dashboard/jvm/src/IssueSearchService.scala` | Rename sweep |
| `dashboard/jvm/src/WorktreeListView.scala` | Call site updated; file is dead code on the live route map (noted, not removed) |

### Modified — Frontend + Tests + Docs

| File | Change Summary |
|------|----------------|
| `dashboard/jvm/resources/static/dashboard.css` | Added `.repo-link`, `.repo-button`, `.repo-button:hover` rules |
| `core/test/ConfigFileTest.scala` | Rename sweep in assertions |
| `core/test/ConfigTest.scala` | New validation rejection cases |
| `core/test/TestFixtures.scala` | Rename sweep |
| `core/test/TrackerUrlBuilderTest.scala` | Rename sweep |
| `dashboard/jvm/test/src/WorktreeCardRendererTest.scala` | 5 new cases; existing cases updated for `PrDisplayData` wrapper |
| `dashboard/jvm/test/src/WorktreeListSyncTest.scala` | Staleness mapping cases; existing call sites updated |
| `dashboard/jvm/test/src/WorktreeCardServiceTest.scala` | Call site updates for new `renderCard` shape |
| `dashboard/jvm/test/src/ProjectDetailsViewTest.scala` | `repoUrl` propagation update |
| `dashboard/jvm/test/src/IssueSearchServiceTest.scala` | Rename sweep |
| `test/config.bats` | Renamed test for `trackerBaseUrl` alias |
| `docs/api/Config.md` | Regenerated to reflect `trackerBaseUrl` |
| `docs/api/YouTrackClient.md` | Regenerated to reflect `trackerBaseUrl` |

<details>
<summary>Workflow / PM files (not reviewable code)</summary>

`project-management/issues/IW-347/analysis.md`,
`project-management/issues/IW-347/cache-investigation.md`,
`project-management/issues/IW-347/cache-rework-issue-draft.md`,
`project-management/issues/IW-347/implementation-log.md`,
`project-management/issues/IW-347/phase-01-context.md`,
`project-management/issues/IW-347/phase-01-tasks.md`,
`project-management/issues/IW-347/phase-02-context.md`,
`project-management/issues/IW-347/phase-02-tasks.md`,
`project-management/issues/IW-347/review-phase-02-20260430-142658.md`,
`project-management/issues/IW-347/review-state.json`,
`project-management/issues/IW-347/tasks.md`

</details>

## Security Notes

Two security issues found and fixed during code review (both are addressed in the shipped code):

1. **`javascript:` URL injection via `trackerBaseUrl`** — a malformed `trackerBaseUrl` could reach the repo-link `href` attribute. Fixed at two layers: parse-time rejection in `Config.scala` (non-http(s) values rejected with an error) and a scheme allow-list filter in `RepoUrlBuilder` as defence in depth.

2. **Weak `repository` validation enabling open-redirect / path-traversal** — `repository` is interpolated directly into a URL path. Fixed at parse time: segments containing characters outside `[A-Za-z0-9._-]+` are rejected; `.` and `..` segments are explicitly rejected. Both fixes have dedicated unit tests in `ConfigTest.scala` and `RepoUrlBuilderTest.scala`.
