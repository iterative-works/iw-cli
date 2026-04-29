# Implementation Tasks: Dashboard — Add git repo web link and ensure PR link is always visible

**Issue:** IW-347
**Created:** 2026-04-29
**Status:** 0/2 phases complete (0%)

## Phase Index

- [ ] Phase 1: Cache-architecture investigation (Est: 0.75–1.25h) → `phase-01-context.md`
- [ ] Phase 2: Repo link + stale-PR badge (Est: 3.25–7.25h) → `phase-02-context.md`

## Progress Tracker

**Completed:** 0/2 phases
**Estimated Total:** 4.0–8.5 hours
**Time Spent:** 0 hours

## Notes

- Phase context files are generated just-in-time during implementation.
- Use `wf-implement` to start the next phase automatically.
- Estimates are rough and will be refined during implementation.
- Phase 1 is a discovery phase: deliverable is `cache-investigation.md` plus a draft for a separate parent issue covering cache-architecture rework. No code changes ship in Phase 1.
- Phase 2 is the implementation phase: domain (`RepoUrlBuilder` + `youtrackBaseUrl` → `trackerBaseUrl` rename) → application/infra plumbing (`PrDisplayData` view model, staleness mapping in `WorktreeListSync`) → presentation (`WorktreeCardRenderer` repo-link section + stale-PR badge) → frontend CSS → tests. All sub-layers fold into this single phase to keep the PR coherent and well above the phase-size floor.
- Phase 1's write-up may inform Phase 2's tests but is not strictly blocking; either order works.
