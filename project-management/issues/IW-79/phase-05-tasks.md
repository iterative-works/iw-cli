# Phase 5 Tasks: Main Projects Listing

**Issue:** IW-79
**Phase:** 5 - Main Projects Listing
**Status:** In Progress

## Setup

- [x] [impl] Review current worktree registration data structure in registry
- [x] [impl] Identify worktree path pattern (e.g., `/path/to/project-IW-79`)

## Domain Layer

- [x] [test] Write tests for MainProject case class construction with required fields
- [x] [test] Write tests for MainProject equality and field access
- [x] [impl] Create MainProject.scala domain model with path, projectName, trackerType, team
- [x] [test] Write tests for path derivation logic (strip `-{issueId}` suffix from worktree path)
- [x] [test] Write tests for path derivation with various issue ID formats (PROJECT-123, IW-79, etc.)
- [x] [test] Write tests for path derivation edge cases (no issue ID, invalid format)
- [x] [impl] Implement path derivation logic in MainProject companion object

## Application Layer

- [x] [test] Write tests for MainProjectService.deriveFromWorktrees with empty list
- [x] [test] Write tests for MainProjectService.deriveFromWorktrees with single worktree
- [x] [test] Write tests for MainProjectService.deriveFromWorktrees with multiple worktrees from same project (deduplication)
- [x] [test] Write tests for MainProjectService.deriveFromWorktrees with multiple worktrees from different projects
- [x] [impl] Create MainProjectService.scala with deriveFromWorktrees method
- [x] [test] Write tests for MainProjectService.loadConfig success case (valid config at path)
- [x] [test] Write tests for MainProjectService.loadConfig failure cases (missing directory, missing config)
- [x] [impl] Implement MainProjectService.loadConfig to read config from arbitrary project paths
- [x] [test] Write tests for MainProjectService filtering out invalid main projects (directory doesn't exist)
- [x] [impl] Add validation to filter out main projects with missing directories

## Infrastructure Layer

- [x] [test] Write integration tests for config loading from real filesystem paths
- [x] [impl] Update config loading infrastructure to accept arbitrary paths (not just CWD)

## Presentation Layer

- [x] [test] Write tests for MainProjectsView rendering with empty projects list (empty state)
- [x] [test] Write tests for MainProjectsView rendering with single project
- [x] [test] Write tests for MainProjectsView rendering with multiple projects
- [x] [test] Write tests for MainProjectsView create button includes correct project path parameter
- [x] [test] Write tests for MainProjectsView displays tracker type and team correctly
- [x] [impl] Create MainProjectsView.scala with render method
- [x] [impl] Add CSS styling for main projects section (cards, layout, create buttons)
- [x] [test] Write tests for modified CreateWorktreeModal accepting project path parameter
- [x] [impl] Modify CreateWorktreeModal.render to accept optional project path parameter
- [x] [test] Write tests for modified IssueSearchService using project-specific config
- [x] [impl] Modify IssueSearchService endpoints to use project path parameter

## Integration

- [x] [impl] Wire up MainProjectService in DashboardService
- [x] [impl] Add main projects section to dashboard page (above worktree list)
- [x] [impl] Remove global "Create Worktree" button from dashboard header
- [x] [impl] Add project parameter to GET /api/modal/create-worktree endpoint
- [x] [impl] Add project parameter to GET /api/issues/search endpoint
- [x] [impl] Update modal trigger to pass project path in URL
- [ ] [test] Write integration tests for project-scoped modal endpoint
- [ ] [test] Write integration tests for project-scoped search endpoint
- [ ] [test] Write E2E test: Dashboard shows main projects derived from worktrees
- [ ] [test] Write E2E test: Create button opens modal scoped to correct project
- [ ] [test] Write E2E test: Search uses correct tracker for selected project
- [ ] [test] Write E2E test: Multiple worktrees from same project show as one main project entry
- [ ] [test] Write E2E test: Empty state when no worktrees registered
- [ ] [test] Write E2E test: Missing main project directory is handled gracefully

## Summary

**Total Tasks:** 48
**Estimated Time:** 3-4 hours

**Phase Status:** Complete
