# Cache architecture investigation (IW-347, Phase 1)

This is a discovery write-up — no code is changed in this phase. The aim is to
characterise how the dashboard's caching architecture currently works, who
populates and reads the caches, and to tie three user-visible symptoms to
specific code paths so a follow-up parent issue can be planned.

All file references are to `/home/mph/Devel/iw/iw-cli-IW-347/...`.

---

## 1. Cache services inventory

The cache layer lives entirely in `dashboard/jvm/src/`. There is no
`core/`-side cache implementation; cache state is coordinated by a single
service (`ServerStateService`) and accessed through pure helper services
that each own one slice of behaviour.

### `ServerStateService` — `dashboard/jvm/src/ServerStateService.scala`

The owner of cache state. Holds the entire `ServerState` (see
`core/model/ServerState.scala`) in a single `AtomicReference`, guarded by a
`ReentrantLock` for writes. Persists every write to disk via `StateRepository`
(JSON file at `~/.local/share/iw/server/state.json`).

State maps it owns (all keyed by `issueId: String`):

- `worktrees: Map[String, WorktreeRegistration]`
- `issueCache: Map[String, CachedIssue]`        — tracker issue title/status/assignee/url
- `progressCache: Map[String, CachedProgress]`  — workflow progress (phases/tasks)
- `prCache: Map[String, CachedPR]`              — pull request URL/state/number
- `reviewStateCache: Map[String, CachedReviewState]` — `review-state.json` contents
- `projects: Map[String, ProjectRegistration]`

Public update API: `updateWorktree`, `updateIssueCache`, `updateProgressCache`,
`updatePRCache`, `updateReviewStateCache`, `updateProject`, plus
`pruneWorktrees`/`pruneProjects`. Reads use `getState` (lock-free via the
`AtomicReference`).

There is no read-through, refresh-on-miss, or "warm" API on this service. It
is a pure write coordinator.

### `IssueCacheService` — `dashboard/jvm/src/IssueCacheService.scala`

Stateless object. Two relevant methods:

- `getCachedOnly(issueId, cache) -> Option[IssueData]` — pure cache lookup, no
  I/O, returns stale data unconditionally if it exists.
- `fetchWithCache(issueId, cache, now, fetchFn, urlBuilder)` — TTL-aware: if
  cache valid → return cached; else call `fetchFn` and on failure fall back
  to stale cache.

Also owns `buildIssueUrl` (Linear/YouTrack/GitHub/GitLab URL formatting).

### `PullRequestCacheService` — `dashboard/jvm/src/PullRequestCacheService.scala`

Stateless object. Mirrors `IssueCacheService`:

- `getCachedOnly(issueId, cache) -> Option[PullRequestData]`
- `fetchPR(worktreePath, cache, issueId, now, execCommand, detectTool)` —
  TTL-aware, falls back to stale cache on CLI failure. Detects `gh` then
  `glab`; parses JSON from `gh pr view` / `glab mr view`.

### `GitStatusService` — `dashboard/jvm/src/GitStatusService.scala`

Stateless object with a single function `getGitStatus(worktreePath, execCommand)`
that runs `git rev-parse --abbrev-ref HEAD` and `git status --porcelain` to
build a `GitStatus`. **This service has no cache** — git status is recomputed
on every call. It is included with the cache services because it is one of
the per-card data sources.

### `RefreshThrottle` — `dashboard/jvm/src/RefreshThrottle.scala`

Stateful, mutable. A `RefreshThrottle` instance is held by `CaskServer` and
shared across all worktree cards. State: a `mutable.Map[issueId, Instant]`
of last-refresh timestamps. Public API:

- `shouldRefresh(issueId, now): Boolean` — true if never refreshed or last
  refresh is ≥ 30 s ago.
- `recordRefresh(issueId, timestamp): Unit`

Hard-coded throttle window: `throttleSeconds = 30L`. Not configurable.

### Cache TTLs — `core/model/CacheConfig.scala`

- Issue cache TTL: 30 min (env `IW_ISSUE_CACHE_TTL_MINUTES`).
- PR cache TTL: 15 min (env `IW_PR_CACHE_TTL_MINUTES`).

These TTLs gate the `fetchWithCache`/`fetchPR` "valid vs stale" decision; the
`RefreshThrottle` 30 s window is an additional, independent gate that prevents
re-fetching at all (regardless of TTL) more than once per 30 s per worktree.

---

## 2. Write paths

Every cache write goes through `ServerStateService.update*Cache` (which
persists state to disk). The triggers are:

### Issue / progress / PR / review-state cache (per-worktree caches)

Written from two HTTP endpoints in `dashboard/jvm/src/CaskServer.scala`:

1. `GET /worktrees/:issueId/card` (lines 314–399) — used by HTMX polling on
   each card.
2. `GET /worktrees/:issueId/detail-content` (lines 401–507) — used by the
   detail-page polling.

Both call `WorktreeCardService.renderCard`, then write any
`fetchedIssue`/`fetchedProgress`/`fetchedPR`/`fetchedReviewState` returned in
the `CardRenderResult` back to `ServerStateService` (`CaskServer.scala`
lines 380–394 and 455–468).

`WorktreeCardService.renderCard` itself fetches fresh data only when
`refreshThrottle.shouldRefresh(issueId, now) == true`
(`WorktreeCardService.scala` line 100). So a write to `issueCache`/`prCache`
only happens at most once per 30 s per worktree, only while a browser is
holding open one of the two card endpoints.

Progress and review-state caches are slightly different: they are
mtime-validated on every render against the on-disk task/review-state files
inside the worktree, so they self-heal independently of `RefreshThrottle`
(`WorktreeCardService.scala` lines 132–136).

### Worktree registrations

Written via `PUT /api/v1/worktrees/:issueId` (CLI-driven, called by
`commands/start.scala` and friends through `ServerClient`). This endpoint
populates `worktrees` but does NOT pre-populate the per-worktree caches.

### Implicit cache deletes

`pruneWorktrees` (called from the `/` and `/projects/...` request handlers,
`CaskServer.scala` line 89) drops cache entries for any worktrees whose
filesystem path no longer exists. `WorktreeUnregistrationService` likewise
clears the four per-worktree caches.

### What does NOT write the caches

- `ServerDaemon.main` (`dashboard/jvm/src/ServerDaemon.scala`) does no
  background work. There is no `ScheduledExecutorService`, no warm-up loop,
  no startup priming.
- `GET /` (root dashboard) reads `reviewStateCache` to compute attention badges
  but never writes any cache.
- `GET /projects/:projectName` reads caches to render the project's worktree
  list, but uses only `*CachedOnly` lookups. It writes nothing.
- `GET /worktrees/:issueId` (detail page initial render) — same: cached-only
  reads, no writes.
- `GET /api/v1/worktrees/:issueId/status` (CLI-facing status API) — reads
  `state.issueCache`/`state.prCache` directly, never fetches, never writes
  (`CaskServer.scala` `assembleWorktreeStatus`, lines 1161–1197).
- `GET /api/worktrees/changes` and `GET /api/projects/:projectName/worktrees/changes`
  — read all four caches via `WorktreeListSync.generateChangesResponse` to
  build OOB swap HTML for additions, never write.
- No CLI command writes a per-worktree cache. `commands/worktrees.scala` and
  `commands/status.scala` are pure readers.

**Net effect:** the `issueCache` and `prCache` for a given worktree are
populated only when a browser actively holds a card endpoint open for that
worktree, and stay unpopulated otherwise.

---

## 3. Read paths

### Browser, dashboard pages (read-only renders)

- `GET /` → reads `reviewStateCache` only.
- `GET /projects/:projectName` → for each filtered worktree, reads
  `issueCache`, `prCache`, `progressCache`, `reviewStateCache` via
  `DashboardService.fetchIssueForWorktreeCachedOnly` /
  `fetchPRForWorktreeCachedOnly`. **On miss, renders a skeleton card** that
  HTMX-polls `/worktrees/:issueId/card`.
- `GET /worktrees/:issueId` → same cached-only pattern; renders detail-page
  skeleton on miss; `WorktreeDetailView` then HTMX-polls
  `/worktrees/:issueId/detail-content`.

### Browser, polling endpoints (read-or-fetch)

- `GET /worktrees/:issueId/card` and `GET /worktrees/:issueId/detail-content`
  → call `WorktreeCardService.renderCard`, which checks
  `refreshThrottle.shouldRefresh`. On `true`: fetches issue + PR (live),
  writes results. On `false`: reads `issueCache`/`prCache` only.
- `GET /api/worktrees/changes` and project-scoped variant → read all four
  per-worktree caches and emit OOB swap HTML; never fetch.

### CLI

- `commands/status.scala` → `ServerClient.getWorktreeStatus(issueId)` →
  `GET /api/v1/worktrees/:issueId/status` → `CaskServer.assembleWorktreeStatus`,
  which reads `state.issueCache.get(issueId)` and `state.prCache.get(issueId)`
  directly. Live data: branch name, working-tree clean/dirty, review state
  (parsed from filesystem), progress (parsed from filesystem). Cached data
  (the part that comes from APIs/`gh`): issue title, issue status, issue url,
  PR url, PR state, PR number. **On miss, all of those fields are `null`.**
- `commands/worktrees.scala` → reads `~/.local/share/iw/server/state.json`
  directly via `core/adapters/StateReader.scala`, then looks up
  `state.issueCache.get(issueId)`, `state.prCache.get(issueId)`,
  `state.reviewStateCache.get(issueId)`, `state.progressCache.get(issueId)`.
  Same outcome: missing entries → blank fields in the table / `null`s in JSON.

### Staleness handling

- Reads via `*CachedOnly` (the dashboard initial-render path and the CLI
  paths) always return whatever is cached, regardless of TTL. Staleness is
  not surfaced to the CLI at all.
- The `/card` and `/detail-content` endpoints use TTL via
  `IssueCacheService.fetchWithCache` / `PullRequestCacheService.fetchPR` —
  these treat cache > TTL as "expired" and try to re-fetch, falling back to
  the stale cached value if the API/CLI call fails.

### Refute the "dashboard-only" hypothesis

The analysis hypothesised that caches are written and read entirely inside
the dashboard. That's true for **writes**. Reads are different: the CLI
reads cache state — directly (via `StateReader` reading the persisted state
file in `commands/worktrees.scala`) and indirectly (via the dashboard's
`/api/v1/worktrees/:issueId/status` JSON endpoint in `commands/status.scala`).
The CLI is therefore a real consumer of the cache, but never a producer of
it; it is silently coupled to whether or not a browser has happened to
visit the worktree.

---

## 4. Refresh triggers

There are exactly four ways a per-worktree cache entry can be refreshed,
and all of them are visit-driven from a browser. There is no scheduled,
background, or CLI-side trigger.

1. **HTMX polling on the dashboard project page.** The skeleton/full card
   in `ProjectDetailsView` carries
   `hx-trigger = "every 30s, refresh from:body"` (`HtmxCardConfig.dashboard`,
   `WorktreeCardRenderer.scala:44`). Note: there is **no `load` trigger** on
   this skeleton — the first poll fires 30 s after the page is loaded.
2. **HTMX polling on the worktree detail page.** Same trigger as above,
   except the polled URL is `/worktrees/:issueId/detail-content`.
3. **HTMX list-changes polling.** `WorktreeListView` (root list) sets
   `hx-trigger = "every 30s"` on the list container, polling
   `/api/worktrees/changes`. This refresh path only emits HTML for additions,
   deletions and reorders — it does **not** trigger any cache write.
4. **Manual refresh via `GET /api/worktrees/:issueId/refresh`.** This
   endpoint just records a throttle reset; it does no fetching itself
   (`CaskServer.scala` lines 509–519). It is not currently called from any
   CLI command found in the repo.

`RefreshThrottle`'s role: gate (2) and (3) per-worktree to at most one
fetch every 30 s, regardless of how many browser tabs / cards are polling.
Note that the throttle's "last refreshed" timestamp is only recorded after
a successful issue fetch (`WorktreeCardService.scala` line 108), so when an
API call consistently fails (e.g., missing `LINEAR_API_TOKEN`), every poll
will retry and the throttle never closes.

There is no scheduled task in `ServerDaemon` (`dashboard/jvm/src/ServerDaemon.scala`).
The server boots, loads state, opens the HTTP port, and waits.

---

## 5. Reproduction notes for the three user-visible symptoms

The phase context permits inferring symptoms from the code path rather than
spinning up the dashboard. All three are characterised here from the code;
none was reproduced live this phase.

### Symptom A — CLI shows nothing for unvisited worktrees

**Observed (inferred from code):** `iw status IWLE-N` and `iw worktrees` on
a freshly-registered worktree that has never been visited in the browser
will show no issue title, no issue status, no PR link, no PR state. JSON
mode emits `null`s in those fields.

**Code path:**

- `commands/status.scala` line 46 → `ServerClient.getWorktreeStatus(issueId.value)`.
- `core/adapters/ServerClient.scala` line 295–325 → `GET /api/v1/worktrees/:issueId/status`.
- `dashboard/jvm/src/CaskServer.scala` line 666–689 (`worktreeStatus` route)
  → `assembleWorktreeStatus`.
- `assembleWorktreeStatus` (lines 1161–1197) reads
  `state.issueCache.get(issueId)` and `state.prCache.get(issueId)`. No
  fetch. The fields it pulls from those caches
  (`issueTitle`/`issueStatus`/`issueUrl`/`prUrl`/`prState`/`prNumber`) are
  serialised as `Option`s and become `null` in JSON when the cache is empty.

The same root cause covers `commands/worktrees.scala` (lines 44–53), which
reads `state.issueCache` / `state.prCache` directly off the persisted
`state.json` file via `StateReader`.

**The cache is empty because nothing populates it server-side until the
browser polls `/worktrees/:issueId/card`** (see §2, write paths).

### Symptom B — first dashboard visit lags ~30 s before issue/PR data appears

**Observed (inferred from code):** opening `/projects/:projectName` (or
`/worktrees/:issueId`) for a worktree that has not been visited recently
shows a "Loading…" skeleton card; the real issue title, status, and PR
link appear roughly 30 s later.

**Code path:**

- `CaskServer.scala` `/projects/:projectName` (lines 162–203): for each
  filtered worktree, calls `DashboardService.fetchIssueForWorktreeCachedOnly`
  / `fetchPRForWorktreeCachedOnly`. On miss → returns `None`.
- `ProjectDetailsView.renderWorktreeCard` (line 172–195): on `issueData ==
  None`, renders `WorktreeCardRenderer.renderSkeletonCard(..., HtmxCardConfig.dashboard)`.
- `HtmxCardConfig.dashboard` (`WorktreeCardRenderer.scala` line 44):
  `trigger = "every 30s, refresh from:body"`. **No `load` trigger.** HTMX's
  `every 30s` fires its first request at +30 s, not immediately.
- That first poll hits `/worktrees/:issueId/card`, which finally calls
  `WorktreeCardService.renderCard` with `refreshThrottle.shouldRefresh ==
  true` (never refreshed) and performs the fetch.

So the lag is exactly the 30 s delay between "skeleton served" and "first
HTMX poll fires". The actual fetch (Linear/GitHub API + `gh pr view`) is
fast on a wired connection; the 30 s lives in the trigger schedule.

(Aside: `WorktreeListView` on root project lists *does* emit a staggered
`load delay:500ms/2s/5s` skeleton trigger — see `WorktreeListView.scala`
lines 102–106 — but that view is not currently mounted by any
`CaskServer` route. The actively-used pages, `ProjectDetailsView` and
`WorktreeDetailView`, both use `HtmxCardConfig.dashboard` without a `load`
trigger.)

### Symptom C — PR link flicker

**Observed (inferred from code):** the PR-link/badge block on a card
sometimes appears, then disappears, then re-appears across consecutive
30 s polls.

**Most likely code path:** `WorktreeCardService.renderCard` lines 99–172
treats issue and PR fetches under a single `shouldFetch` gate driven by
`RefreshThrottle.shouldRefresh`. `recordRefresh` is called only after a
**successful issue fetch** (line 108). Two consequences:

1. When a tracker API call fails transiently (rate limit, token blip), the
   throttle is not recorded, so the next poll re-fetches everything,
   including the PR. If `gh pr view` fails on that poll (PR was just
   merged/closed and the tool returns non-zero), `fetchFreshPR` returns
   `Right(None)` — and `WorktreeCardService.renderCard` line 155–166 then
   falls back to `prCache.get(issueId)` for the rendered card. If the PR
   cache was never populated (or was just expired), `prData = None` →
   the PR section disappears for that render. On the following poll the
   path re-tries and the section reappears.
2. The same non-recorded-throttle case can produce a transient gap if the
   gh CLI is briefly unavailable on `PATH` for the `CommandRunner` exec.

A secondary contributor: `WorktreeCardRenderer.renderCard` at
`WorktreeCardRenderer.scala` line 146 conditionally renders the entire PR
block on `prData.map { … }`. There is no placeholder slot; on a render
where `prData = None`, the section is absent from the DOM (rather than
being marked stale-but-present), so HTMX `outerHTML` swaps cleanly remove
and re-add it across polls — visible to the user as flicker.

The Phase-2 mitigation tracked elsewhere (renderer-side) presumably
addresses point 2 (the renderer); the underlying cause (point 1, fetch
volatility) is in the cache + throttle interaction and belongs to the
follow-up cache rework.

---

## 6. Open questions

These are the threads that came up during the time-box and warrant a
deeper look in the follow-up issue rather than expanding Phase 1.

- **Throttle recording is gated on issue fetch, not PR fetch.** Deliberate
  or accidental? The current behaviour means that an environment with no
  Linear/YouTrack token but a working `gh` will hammer `gh pr view` every
  poll. It also produces the symptom-C flicker mechanism above. The
  follow-up should decide whether `RefreshThrottle` should be per-resource
  (issue, PR, progress) or per-worktree.
- **Two TTLs and one throttle, all 30-ish s/15-ish min.** `IssueCacheService`
  and `PullRequestCacheService` already do TTL-aware fallback. The 30 s
  `RefreshThrottle` is a separate gate. With the throttle in place, the
  TTL never matters in practice (a poll inside 30 s never reaches the TTL
  check; a poll past 30 s always tries to re-fetch regardless of TTL).
  The follow-up should pick one staleness model.
- **No scheduled refresh in `ServerDaemon`.** The "background warm-up"
  proposal in the analysis can be implemented either as a scheduled
  executor in the dashboard or as a CLI-driven population (e.g.,
  `iw status` or `iw start` warming the cache via the existing
  `update*Cache` API). The follow-up should choose; the existing
  `ServerStateService` is already shaped right for either.
- **No CLI write path to the cache.** Today there is no API endpoint or
  internal hook for the CLI to populate `issueCache`/`prCache` without
  going through the browser-facing card endpoint. The follow-up will
  likely need to add one (e.g., `POST /api/v1/worktrees/:issueId/refresh`
  that actually fetches, not just resets the throttle).
- **`WorktreeListView` is dead code on the live route map.** Its
  `load delay:Xs` skeleton is not in use; only `ProjectDetailsView`'s
  no-load skeleton is. Worth cleaning up regardless of the larger rework.
- **`*CachedOnly` proliferation.** `DashboardService` exposes
  `fetchIssueForWorktreeCachedOnly` / `fetchPRForWorktreeCachedOnly`
  alongside the fetching variants. The split exists because the initial
  page render is supposed to be non-blocking, but the result is that
  every "read" path silently chooses between "real data" and "skeleton"
  with no visible indication of why. Worth considering as part of the
  redesign.
- **PR section presence/absence vs. stale flag.** Issue data renders a
  `cached`/`stale` indicator; PR data has no equivalent. A follow-up could
  surface PR cache state symmetrically to remove the renderer-side root
  cause of symptom C.

The 1.25 h time-box was respected; nothing here is left ambiguous because
of overshooting, only because the redesign legitimately belongs to the
follow-up issue.
