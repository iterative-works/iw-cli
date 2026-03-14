---
generated_from: 824da2054da1ba22988cd187d29fbc130f6be407
generated_at: 2026-03-14T20:48:27Z
branch: IW-188-phase-02
issue_id: IW-188
phase: 2
files_analyzed:
  - .iw/core/test/WorktreeDetailViewTest.scala
  - .iw/core/test/CaskServerTest.scala
  - .iw/test/dashboard-dev-mode.bats
  - project-management/issues/IW-188/phase-02-context.md
  - project-management/issues/IW-188/phase-02-tasks.md
---

# Review Packet: Phase 2 - Breadcrumb navigation with project context

## Goals

Phase 2 verifies and strengthens the breadcrumb implementation that was already built in Phase 1. The breadcrumb renders a `Projects > {projectName} > {issueId}` hierarchy on the worktree detail page, derived from the worktree's filesystem path. Phase 1 had working code but insufficient test coverage to prove the acceptance criteria — specifically, no test verified the project link URL, no test confirmed the issue ID was not rendered as a link, and there was no E2E test at all for the worktree detail page.

Key objectives:
- Add the missing assertion that the project link URL (`href="/projects/{projectName}"`) is rendered correctly in the three-level breadcrumb
- Add a dedicated test that the issue ID in the breadcrumb is plain text, not an anchor tag pointing to `/worktrees/{issueId}`
- Strengthen the not-found breadcrumb test by asserting the root link (`href="/"`) is present
- Strengthen the two-level (no project) breadcrumb test by asserting no project link appears
- Add breadcrumb assertions to two existing integration tests in `CaskServerTest`
- Add a new E2E BATS test that starts the dev server, registers a worktree, and asserts breadcrumb content in the HTTP response

## Scenarios

- [ ] Breadcrumb shows three levels (Projects > projectName > issueId) when project is derivable from the worktree path
- [ ] The project name segment in the three-level breadcrumb links to `/projects/{projectName}`
- [ ] The issue ID in the breadcrumb is not wrapped in a link (it is the current page)
- [ ] Breadcrumb shows two levels (Projects > issueId) when no project can be derived from the worktree path
- [ ] The two-level breadcrumb does not contain any `/projects/` link
- [ ] The "Projects" segment links to `/` in both the standard and not-found breadcrumb
- [ ] The not-found page (404) includes the breadcrumb with the `breadcrumb` CSS class
- [ ] `GET /worktrees/:issueId` HTTP response contains `breadcrumb` class and "Projects" text
- [ ] `GET /worktrees/NONEXISTENT` 404 HTTP response contains `breadcrumb` class
- [ ] E2E: dev server responds with breadcrumb content on the worktree detail page

## Entry Points

| File | Method/Class | Why Start Here |
|------|--------------|----------------|
| `.iw/core/test/WorktreeDetailViewTest.scala` | `renderDefault()` helper + breadcrumb tests | Unit tests for the breadcrumb HTML; four tests are new or strengthened in this phase |
| `.iw/core/test/CaskServerTest.scala` | `"GET /worktrees/:issueId returns 200 with HTML for known worktree"` | Integration test with two new breadcrumb assertions |
| `.iw/core/test/CaskServerTest.scala` | `"GET /worktrees/NONEXISTENT returns 404 with error page"` | Integration test with one new breadcrumb assertion on the error path |
| `.iw/test/dashboard-dev-mode.bats` | `"GET /worktrees/:issueId returns breadcrumb navigation"` | New BATS E2E test that boots a real server and hits the route |

## Diagrams

### What Phase 2 changes (diff summary)

Phase 2 is pure test additions. No production code changed. The following diagram shows where new assertions attach to existing tests vs. entirely new tests.

```
WorktreeDetailViewTest.scala
├── "render shows breadcrumb with project name when derivable"
│     ADDED: assert href="/projects/iw-cli" present
│
├── "render shows breadcrumb without project name when not derivable"
│     ADDED: assert href="/projects/" absent
│
├── NEW TEST "breadcrumb issueId is not a link"
│     assert href="/worktrees/IW-188" absent in breadcrumb
│
└── "renderNotFound includes breadcrumb"
      ADDED: assert href="/" present

CaskServerTest.scala
├── "GET /worktrees/:issueId returns 200 with HTML for known worktree"
│     ADDED: assert html contains "breadcrumb"
│     ADDED: assert html contains "Projects"
│
└── "GET /worktrees/NONEXISTENT returns 404 with error page"
      ADDED: assert html contains "breadcrumb"

dashboard-dev-mode.bats
└── NEW TEST "GET /worktrees/:issueId returns breadcrumb navigation"
      Start --dev server → register worktree → GET /worktrees/TEST-1
      assert "breadcrumb", "Projects", "TEST-1" in response body
```

### Breadcrumb rendering logic (unchanged from Phase 1)

```
WorktreeDetailView.render(worktree, projectName, ...)
  └── renderBreadcrumb(issueId, projectName)
        projectName = Some(name) → <nav class="breadcrumb">
                                     <a href="/">Projects</a> >
                                     <a href="/projects/{name}">{name}</a> >
                                     <span>{issueId}</span>
                                   </nav>
        projectName = None       → <nav class="breadcrumb">
                                     <a href="/">Projects</a> >
                                     <span>{issueId}</span>
                                   </nav>

WorktreeDetailView.renderNotFound(issueId)
  └── renderBreadcrumb(issueId, None)   ← always two-level
```

### Project name derivation (unchanged from Phase 1)

```
CaskServer.worktreeDetail route
  └── MainProject.deriveMainProjectPath(worktree.path)
        e.g. "/home/user/projects/iw-cli-IW-188"
             → Some("/home/user/projects/iw-cli")
             → os.Path(...).last → Some("iw-cli")
        No match → None
  └── WorktreeDetailView.render(..., projectName = derived)
```

## Test Summary

| Test | File | Type | What it verifies |
|------|------|------|-----------------|
| "render shows breadcrumb with project name when derivable" (strengthened) | `WorktreeDetailViewTest.scala` | Unit | Three-level breadcrumb contains project name, issue ID, Projects link, root href, and **now also** `href="/projects/iw-cli"` |
| "render shows breadcrumb without project name when not derivable" (strengthened) | `WorktreeDetailViewTest.scala` | Unit | Two-level breadcrumb has Projects, issue ID, root href, and **now also** no `/projects/` link |
| "breadcrumb issueId is not a link" (new) | `WorktreeDetailViewTest.scala` | Unit | Issue ID segment in breadcrumb does NOT have `href="/worktrees/IW-188"` |
| "renderNotFound includes breadcrumb" (strengthened) | `WorktreeDetailViewTest.scala` | Unit | Not-found breadcrumb has `breadcrumb` class, "Projects", and **now also** `href="/"` |
| "GET /worktrees/:issueId returns 200 with HTML for known worktree" (strengthened) | `CaskServerTest.scala` | Integration | 200 response contains `breadcrumb` class and "Projects" text |
| "GET /worktrees/NONEXISTENT returns 404 with error page" (strengthened) | `CaskServerTest.scala` | Integration | 404 response contains `breadcrumb` class |
| "GET /worktrees/:issueId returns breadcrumb navigation" (new) | `dashboard-dev-mode.bats` | E2E | Dev server returns response with `breadcrumb`, "Projects", and the registered issue ID |

All pre-existing unit and integration tests for WorktreeDetailView and the worktree detail route remain unchanged; Phase 2 only adds or strengthens existing tests.

## Files Changed

| File | Change type | Description |
|------|-------------|-------------|
| `.iw/core/test/WorktreeDetailViewTest.scala` | Modified (uncommitted) | Added one new unit test; strengthened three existing tests with breadcrumb URL/link assertions |
| `.iw/core/test/CaskServerTest.scala` | Modified (uncommitted) | Added three breadcrumb assertions to two existing integration tests |
| `.iw/test/dashboard-dev-mode.bats` | Modified (uncommitted) | Added one new E2E BATS test for breadcrumb in worktree detail route |
| `project-management/issues/IW-188/phase-02-tasks.md` | Modified (uncommitted) | All tasks marked complete |
| `project-management/issues/IW-188/review-state.json` | Modified (uncommitted) | Status tracking update |

<details>
<summary>Phase 2 diff summary (uncommitted changes only)</summary>

**WorktreeDetailViewTest.scala** — 4 changes across 3 tests:
1. In `"render shows breadcrumb with project name when derivable"`: added `assert(html.contains("href=\"/projects/iw-cli\""), ...)`
2. In `"render shows breadcrumb without project name when not derivable"`: added `assert(!html.contains("href=\"/projects/"), ...)`
3. New test `"breadcrumb issueId is not a link"`: asserts `!html.contains("href=\"/worktrees/IW-188\"")`
4. In `"renderNotFound includes breadcrumb"`: added `assert(html.contains("href=\"/\""), ...)`

**CaskServerTest.scala** — 3 new assertions in 2 tests:
1. In `"GET /worktrees/:issueId returns 200"`: added breadcrumb class and "Projects" assertions
2. In `"GET /worktrees/NONEXISTENT returns 404"`: added breadcrumb class assertion

**dashboard-dev-mode.bats** — 1 new test (~47 lines):
- Starts `iw dashboard --dev`, extracts dynamic port from stdout, polls `/health`, registers a worktree via PUT, GETs `/worktrees/TEST-1`, asserts breadcrumb class, "Projects", and issue ID are present in the response body.

</details>
