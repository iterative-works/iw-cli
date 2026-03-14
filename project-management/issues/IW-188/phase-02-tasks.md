# Phase 2 Tasks: Breadcrumb navigation with project context

## Setup

- [x] [setup] Read existing breadcrumb tests in `WorktreeDetailViewTest.scala` and verify they pass before making changes

## Tests

- [x] [test] Add assertion to "render shows breadcrumb with project name when derivable" that `href="/projects/iw-cli"` appears in the HTML (verifies project link URL, not just text)
- [x] [test] Add unit test: "breadcrumb issueId is not a link" — verify the breadcrumb does NOT contain `href="/worktrees/IW-188"` (issue ID should be plain `<span>`, not `<a>`)
- [x] [test] Add assertion to "renderNotFound includes breadcrumb" that `href="/"` appears in the HTML (verifies the Projects link in not-found breadcrumb is a real link)
- [x] [test] Add assertion to "render shows breadcrumb without project name when not derivable" that breadcrumb does NOT contain `href="/projects/` (no project link when project is unknown)

## Implementation

- [x] [impl] Run tests after adding assertions — if any fail, fix the implementation to make them pass
- [x] [impl] Run full test suite (`./iw test unit`) to verify no regressions

## Integration

- [x] [integ] Add breadcrumb assertions to `CaskServerTest` "GET /worktrees/:issueId returns 200 with HTML for known worktree" — assert response contains `breadcrumb` class and `Projects` text
- [x] [integ] Add breadcrumb assertions to `CaskServerTest` "GET /worktrees/NONEXISTENT returns 404 with error page" — assert response contains `breadcrumb` class
- [x] [integ] Add BATS E2E test in `dashboard-dev-mode.bats`: start dev server, register a worktree via `PUT /api/v1/worktrees/{issueId}`, hit `GET /worktrees/{issueId}`, assert response contains `breadcrumb` and `Projects`
- [x] [integ] Run full test suite (`./iw test`) to confirm all unit, integration, and E2E tests pass
**Phase Status:** Complete
