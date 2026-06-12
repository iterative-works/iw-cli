# Decouple per-worktree caches from dashboard browser visits

## Motivation

The dashboard's per-worktree caches (`issueCache`, `prCache`,
`progressCache`, `reviewStateCache` on `ServerState`) are populated only
when a browser actively polls a worktree's card endpoint. This produces a
class of user-visible bugs that affect both CLI and dashboard users, the
most concrete of which is being tracked as IW-347.

Three symptoms surface from the same root cause — visit-driven cache
population:

1. **The CLI shows nothing for unvisited worktrees.** `iw status` and
   `iw worktrees` read `issueCache` / `prCache` either through the
   `/api/v1/worktrees/:issueId/status` endpoint or directly from the
   on-disk `state.json`. Both paths skip any kind of fetch. If no browser
   has visited the worktree's card or detail page, the issue title,
   issue status, and PR link are empty in the CLI output (`null` in
   `--json`). Users observe this as "the CLI is broken on a fresh
   worktree".

2. **The first dashboard visit lags ~30 s before issue/PR data shows up.**
   The initial render of `/projects/:projectName` and `/worktrees/:issueId`
   uses cached-only lookups. On a cache miss, a skeleton card is served
   with `hx-trigger = "every 30s, refresh from:body"` — there is no
   `load` trigger on this skeleton, so HTMX waits the full 30 s before
   firing the first poll, which is also the first opportunity the server
   has to fetch fresh data. The fetch itself is fast; the 30 s is
   trigger-schedule overhead.

3. **PR-link flicker on cards.** `WorktreeCardService.renderCard`
   gates issue and PR fetches behind a single `RefreshThrottle.shouldRefresh`
   call. The throttle's "last refreshed" timestamp is only recorded
   after a successful tracker-issue fetch, so when the issue fetch fails
   (e.g., missing token, transient API error), every subsequent poll
   re-fetches the PR too. Combined with the renderer rendering the PR
   block conditionally on `prData = Some`, transient `gh pr view`
   failures cause the PR block to vanish and reappear across consecutive
   30 s polls.

IW-347 specifically tracks the renderer-side mitigation for symptom 3.
Symptoms 1 and 2 are unaddressed and have no in-flight fix. All three
share the same underlying problem and a stable fix needs a coherent
approach to cache population, which this issue is for.

## Summary of findings

A full write-up lives in
`project-management/issues/IW-347/cache-investigation.md`. The key facts:

- All cache state is owned by `ServerStateService`
  (`dashboard/jvm/src/ServerStateService.scala`), backed by an
  `AtomicReference[ServerState]` and persisted to
  `~/.local/share/iw/server/state.json` after every write.
- The only code paths that **write** the per-worktree caches are the two
  HTMX card endpoints: `GET /worktrees/:issueId/card` and
  `GET /worktrees/:issueId/detail-content`. Both are browser-driven.
- No CLI command writes the caches. There is no scheduled task in
  `ServerDaemon` (the dashboard does no background work).
- `RefreshThrottle` (30 s, hard-coded) and the per-resource TTLs
  (`CacheConfig`: issue 30 min, PR 15 min) are independent gates with
  overlapping intent — in practice the throttle dominates and the TTL
  rarely fires.
- The CLI silently consumes the cache: `iw status` via the dashboard's
  JSON status endpoint, `iw worktrees` via `StateReader` reading
  `state.json` directly. Neither has a fallback if the cache is empty.
- `assembleWorktreeStatus` (`CaskServer.scala` lines 1161–1197) is the
  CLI-facing read path; it uses `state.issueCache.get(...)` /
  `state.prCache.get(...)` with no fetch on miss.

The architecture is, in short, "browser-first" — but the CLI surfaces
read directly from the same state, so when no browser has been involved,
both surfaces look broken.

## Proposed direction

The follow-up should make the cache a **shared, eagerly-maintained
resource** rather than a side effect of dashboard polling. Three
complementary directions, in rough priority order:

### 1. Background warm-up in the dashboard server

When `CaskServer` starts (or when a worktree is registered via
`PUT /api/v1/worktrees/:issueId`), schedule an initial fetch for that
worktree using the existing `IssueCacheService.fetchWithCache` /
`PullRequestCacheService.fetchPR`. A periodic refresh — say every
N minutes, configurable, gated by `RefreshThrottle` for safety — should
keep the cache warm independently of whether anyone is looking at the
dashboard. The pieces needed already exist (`ServerStateService.update*Cache`,
the fetch helpers in `DashboardService`); what is missing is the
scheduler and the registration-time hook in `ServerDaemon`/`CaskServer`.

This alone fixes symptom 1 (CLI on fresh worktree) for any worktree
registered while the server is running.

### 2. CLI-driven cache population

Expose a real-fetch endpoint (e.g., `POST /api/v1/worktrees/:issueId/refresh`
that actually fetches, unlike the current `GET .../refresh` which only
resets the throttle) and call it implicitly from the CLI commands that
read cache state. `commands/status.scala` is the obvious site: if the
returned `WorktreeStatus` has `null` issue fields, fall through to a
cache-populating call before re-reading. Same shape works for
`commands/worktrees.scala`.

This makes the CLI self-healing in cases where the warm-up loop has not
yet run (e.g., the user just created a worktree and immediately ran
`iw status`).

### 3. Decouple cache freshness from dashboard polling

With (1) and (2) in place, the visit-driven write path in `/card` and
`/detail-content` becomes redundant. The skeleton-then-poll pattern can
be replaced with: dashboard renders directly from the cache (which is
maintained by the warm-up loop), the cache is staleness-aware (`isStale`
indicator already exists for issue data), and the polling endpoint exists
only to refresh the cache, not as a critical path for the user-facing
render. This eliminates symptom 2 (~30 s first-visit lag) entirely and
removes the timing coupling that drives symptom 3 (PR flicker).

A worthwhile cleanup along the way: collapse the parallel concepts of
`RefreshThrottle` and per-resource TTL into a single staleness model.
Remove `WorktreeListView` which is dead code on the live route map.
Surface a "cache state" indicator on the PR block symmetrical to the
issue block's `cached`/`stale` indicator, so renders stay structurally
stable across polls.

## References

- Investigation write-up:
  `project-management/issues/IW-347/cache-investigation.md`
- Motivating issue: IW-347 (PR-link flicker; the renderer-side mitigation
  is being addressed in IW-347 Phase 2; this issue covers the underlying
  cache-architecture rework).
- Key files:
  - `dashboard/jvm/src/ServerStateService.scala` (state owner)
  - `dashboard/jvm/src/IssueCacheService.scala`,
    `dashboard/jvm/src/PullRequestCacheService.scala` (fetch logic)
  - `dashboard/jvm/src/RefreshThrottle.scala` (30 s gate)
  - `dashboard/jvm/src/WorktreeCardService.scala` (single-throttle issue+PR fetch)
  - `dashboard/jvm/src/CaskServer.scala` (`assembleWorktreeStatus`,
    `worktreeCard`, `worktreeDetailContent`)
  - `core/adapters/ServerClient.scala`, `commands/status.scala`,
    `commands/worktrees.scala` (CLI read paths)
