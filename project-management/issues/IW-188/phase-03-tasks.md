# Phase 3 Tasks: Handle unknown worktree gracefully (test coverage)

## Setup

- [ ] [setup] Review existing `renderNotFound` implementation in `WorktreeDetailView.scala` to confirm Scalatags auto-escaping behavior

## Tests

### Unit Tests (WorktreeDetailViewTest.scala)

- [ ] [test] Test `renderNotFound` escapes special characters in issue ID (pass `<script>alert(1)</script>`, assert no raw `<script>` tag, assert `&lt;script&gt;` present)
- [ ] [test] Test `renderNotFound` with empty issue ID (assert still renders "Worktree Not Found" heading and back link)
- [ ] [test] Test `renderNotFound` includes "Back to Projects Overview" link text (not just `href="/"`)
- [ ] [test] Test `renderNotFound` does not contain worktree data section CSS classes (`git-status`, `pr-link`, `progress-bar`, `phase-info`, `zed-link`)

### Integration Tests (CaskServerTest.scala)

- [ ] [test] Test `GET /worktrees/<script>alert(1)</script>` with URL-encoded special characters returns 404 with escaped content (no raw HTML in response body)

### E2E Tests (dashboard-dev-mode.bats)

- [ ] [test] Test `GET /worktrees/NONEXISTENT-999` returns 404 with not-found page content (status code 404, body contains "not registered" or "Not Found", body contains back link)

## Integration

- [ ] [verify] Run `./iw test unit` and confirm all new unit tests pass
- [ ] [verify] Run `./iw test e2e` and confirm all new E2E tests pass
- [ ] [verify] Run `./iw test` and confirm full suite is green with no regressions
