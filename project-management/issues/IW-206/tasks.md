# Implementation Tasks: Dashboard - Add project details page with worktree list

**Issue:** IW-206
**Created:** 2026-02-20
**Status:** 7/7 phases complete (100%)

## Phase Index

- [x] Phase 01: Extract CSS/JS to static resources and create shared layout (Est: 4-6h) → `phase-01-context.md`
- [x] Phase 02: Project details page with filtered worktree cards (Est: 8-12h) → `phase-02-context.md`
- [x] Phase 03: Breadcrumb navigation from project page back to overview (Est: 2-3h) → `phase-03-context.md`
- [x] Phase 04: Project-scoped Create Worktree button (Est: 3-4h) → `phase-04-context.md`
- [x] Phase 05: Project cards on overview link to project details (Est: 2-3h) → `phase-05-context.md`
- [x] Phase 06: Handle unknown project name gracefully (Est: 2-3h) → `phase-06-context.md`
- [x] Phase 07: HTMX auto-refresh for project worktree list (Est: 6-8h) → `phase-07-context.md`

## Progress Tracker

**Completed:** 7/7 phases
**Estimated Total:** 27-39 hours
**Time Spent:** 0 hours

## Notes

- Phase 01 is a pre-requisite refactoring: extract inline CSS/JS from DashboardService, add static resource serving to CaskServer, create shared page layout component
- Phase context files generated just-in-time during implementation
- Use ag-implement to start next phase automatically
- Estimates are rough and will be refined during implementation
- Filtering uses existing path heuristic (MainProject.deriveMainProjectPath); git-based discovery deferred to #208
- New endpoint pattern: GET /api/projects/:projectName/worktrees/changes
