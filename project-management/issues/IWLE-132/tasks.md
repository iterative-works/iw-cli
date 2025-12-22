# Implementation Tasks: Add GitHub Issues support using gh CLI

**Issue:** IWLE-132
**Created:** 2025-12-22
**Status:** 2/6 phases complete (33%)

## Phase Index

- [x] Phase 1: Initialize project with GitHub tracker (Est: 6-8h) → `phase-01-context.md`
- [x] Phase 2: Repository auto-detection from git remote (Est: 4-6h) → `phase-02-context.md`
- [ ] Phase 3: Create GitHub issue via feedback command (Est: 8-12h) → `phase-03-context.md`
- [ ] Phase 4: Handle gh CLI prerequisites (Est: 4-6h) → `phase-04-context.md`
- [ ] Phase 5: Display GitHub issue details (Est: 6-8h) → `phase-05-context.md`
- [ ] Phase 6: Doctor validates GitHub setup (Est: 3-4h) → `phase-06-context.md`

## Progress Tracker

**Completed:** 2/6 phases
**Estimated Total:** 31-44 hours
**Time Spent:** ~4 hours

## Phase Summary

| Phase | Story | Key Deliverables | Dependencies |
|-------|-------|------------------|--------------|
| 1 | Init with GitHub | `IssueTrackerType.GitHub`, config schema, `iw init --tracker github` | None |
| 2 | Auto-detect repo | Parse owner/repo from git remote, store in config | Phase 1 |
| 3 | Feedback command | `GitHubClient.scala`, `gh issue create`, label mapping | Phases 1-2 |
| 4 | Error handling | Detect gh not installed/authenticated, helpful messages | Phase 3 |
| 5 | Issue display | `gh issue view`, numeric ID parsing, issue formatting | Phases 1-2 |
| 6 | Doctor checks | gh CLI validation, auth status, repo access checks | Phases 1-5 |

## Iteration Plan

**Iteration 1** (Phases 1-2): Foundation
- Config model supports GitHub tracker
- Repository auto-detection from git remote
- `iw init --tracker github` works

**Iteration 2** (Phases 3-4): Core Feature
- Feedback command creates GitHub issues
- Error handling for gh CLI issues
- Complete feedback workflow

**Iteration 3** (Phases 5-6): Complete Feature Set
- Issue display for GitHub
- Doctor validates GitHub setup
- Full parity with Linear/YouTrack

## Technical Decisions

All technical decisions resolved - see `analysis.md` for details:
- GitHubClient.scala pattern (consistent with LinearClient)
- Extend IssueId.parse for numeric GitHub IDs
- Hardcoded labels with graceful fallback
- Add `repository` field to config
- Exit codes + stderr for error handling
- Hybrid E2E testing (mock default, real with env var)

## Notes

- Phase context files generated just-in-time during implementation
- Use `/iterative-works:ag-implement` to start next phase automatically
- Estimates are rough and will be refined during implementation
- Each iteration delivers independently usable functionality
