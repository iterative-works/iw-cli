---
generated_from: 1b17050733af401933daed040758e08403444fa4
generated_at: 2026-03-14T22:31:03Z
branch: IW-188-phase-03
issue_id: IW-188
phase: 3
files_analyzed:
  - .iw/core/test/WorktreeDetailViewTest.scala
  - .iw/core/test/CaskServerTest.scala
  - .iw/test/dashboard-dev-mode.bats
---

# Review Packet: Phase 3 - Handle unknown worktree gracefully (test coverage)

## Goals

This phase verifies and strengthens the not-found handling built in Phase 1. The `renderNotFound`
view and 404 route logic are already fully implemented. Phase 3 closes test coverage gaps identified
during gap analysis: XSS safety, edge-case inputs (empty ID, special characters), link text
verification, absence of worktree data sections, and an E2E proof that the 404 flow works
end-to-end.

No production code was modified in this phase. All changes are test additions.

Key objectives:
- Prove that Scalatags auto-escaping prevents XSS when special characters appear in the issue ID
- Verify edge-case inputs (empty string, URL-encoded HTML) render a valid not-found page
- Confirm the "Back to Projects Overview" link text is present (not just the `href`)
- Confirm worktree data sections (`git-status`, `pr-link`, `progress-bar`, `phase-info`, `zed-link`) are absent from the not-found page
- Add an E2E (BATS) test that hits a live server and verifies the 404 response end-to-end

## Scenarios

- [x] Unknown issue IDs display a user-friendly "not found" page (covered since Phase 1; integration test confirms)
- [x] The page includes a link back to the projects overview (link text "Back to Projects Overview" now verified by unit test)
- [x] Page is wrapped in `PageLayout` with consistent styling (covered since Phase 1; integration test verifies `worktree-detail` class)
- [x] Breadcrumb is present on not-found page (covered since Phase 2)
- [x] Special characters in the issue ID are HTML-escaped (XSS-safe) -- new unit test
- [x] Empty issue ID still renders a valid not-found page -- new unit test
- [x] Not-found page does not accidentally render worktree data sections -- new unit test
- [x] Integration test verifies URL-encoded special characters produce escaped HTML in the 404 response -- new integration test
- [x] E2E test proves the 404 flow works against a running server -- new BATS test

## Entry Points

| File | Method/Class | Why Start Here |
|------|--------------|----------------|
| `.iw/core/test/WorktreeDetailViewTest.scala` | `renderNotFound*` tests (lines 241-265) | Four new unit tests covering XSS escaping, empty ID, link text, and absent data sections |
| `.iw/core/test/CaskServerTest.scala` | `"GET /worktrees with URL-encoded special characters..."` (line 1115) | New integration test verifying server-side HTML escaping for a crafted URL |
| `.iw/test/dashboard-dev-mode.bats` | `"GET /worktrees/NONEXISTENT-999 returns not-found page"` (line 128) | New E2E test starting a real server and asserting 404 status and page content |

## Diagrams

### Test coverage map for `renderNotFound`

```
WorktreeDetailView.renderNotFound(issueId)
│
├── Unit tests (WorktreeDetailViewTest.scala)
│   ├── [existing] includes issue ID
│   ├── [existing] includes href="/"
│   ├── [existing] includes breadcrumb
│   ├── [existing] shows heading and "not registered" text
│   ├── [NEW] escapes special characters (XSS prevention)
│   ├── [NEW] empty ID still renders heading and back link
│   ├── [NEW] "Back to Projects Overview" link text present
│   └── [NEW] no worktree data CSS classes (git-status, pr-link, progress-bar, phase-info, zed-link)
│
├── Integration test (CaskServerTest.scala)
│   ├── [existing] GET /worktrees/NONEXISTENT-999 → 404, text/html, breadcrumb, styled page
│   └── [NEW] GET /worktrees/%3Cscript%3E... → 404, no raw <script> tag in body
│
└── E2E test (dashboard-dev-mode.bats)
    └── [NEW] Live server: GET /worktrees/NONEXISTENT-999 → 404, "not registered", href="/", issue ID in body
```

### 404 request flow (implemented in Phase 1, verified in Phase 3)

```
HTTP GET /worktrees/NONEXISTENT-999
        │
        ▼
CaskServer GET /worktrees/:issueId
        │
        ├── state.worktrees.get(issueId) → None
        │
        ▼
WorktreeDetailView.renderNotFound(issueId)
        │
        ├── Scalatags auto-escapes issueId (XSS-safe)
        ├── Renders breadcrumb: Projects > {issueId}
        ├── Renders "Worktree Not Found" heading
        ├── Renders "Worktree '{issueId}' is not registered."
        ├── Renders registration hint
        └── Renders "Back to Projects Overview" (href="/")
        │
        ▼
PageLayout.render(..., statusCode = 404)
        │
        ▼
HTTP 404 text/html; charset=UTF-8
```

## Test Summary

### Unit Tests (new in Phase 3)

| Test | File | Type | Status |
|------|------|------|--------|
| `renderNotFound escapes special characters in issue ID` | `WorktreeDetailViewTest.scala:241` | Unit | Added |
| `renderNotFound with empty issue ID still renders heading and back link` | `WorktreeDetailViewTest.scala:247` | Unit | Added |
| `renderNotFound includes 'Back to Projects Overview' link text` | `WorktreeDetailViewTest.scala:253` | Unit | Added |
| `renderNotFound does not contain worktree data section CSS classes` | `WorktreeDetailViewTest.scala:258` | Unit | Added |

### Unit Tests (pre-existing, Phase 1/2)

| Test | File | Type |
|------|------|------|
| `renderNotFound includes the issue ID` | `WorktreeDetailViewTest.scala:218` | Unit |
| `renderNotFound includes link back to overview` | `WorktreeDetailViewTest.scala:223` | Unit |
| `renderNotFound includes breadcrumb` | `WorktreeDetailViewTest.scala:228` | Unit |
| `renderNotFound shows not found heading and explanation` | `WorktreeDetailViewTest.scala:235` | Unit |

### Integration Tests (new in Phase 3)

| Test | File | Type | Status |
|------|------|------|--------|
| `GET /worktrees with URL-encoded special characters returns 404 with escaped content` | `CaskServerTest.scala:1115` | Integration | Added |

### Integration Tests (pre-existing, Phase 1)

| Test | File | Type |
|------|------|------|
| `GET /worktrees/NONEXISTENT returns 404 with error page` | `CaskServerTest.scala:1083` | Integration |

### E2E Tests (new in Phase 3)

| Test | File | Type | Status |
|------|------|------|--------|
| `GET /worktrees/NONEXISTENT-999 returns not-found page` | `dashboard-dev-mode.bats:128` | E2E | Added |

## Files Changed

| File | Change Type | Description |
|------|-------------|-------------|
| `.iw/core/test/WorktreeDetailViewTest.scala` | Modified | Four new unit tests for `renderNotFound` (lines 241-265) |
| `.iw/core/test/CaskServerTest.scala` | Modified | One new integration test for URL-encoded special characters (lines 1115-1138) |
| `.iw/test/dashboard-dev-mode.bats` | Modified | One new E2E BATS test for the 404 flow (lines 128-176) |

<details>
<summary>WorktreeDetailViewTest.scala - new tests (lines 241-265)</summary>

```scala
test("renderNotFound escapes special characters in issue ID"):
  val html = WorktreeDetailView.renderNotFound("<script>alert(1)</script>").render

  assert(!html.contains("<script>"), "Should HTML-escape special characters in issue ID")
  assert(html.contains("&lt;script&gt;"), "Should contain escaped version of the issue ID")

test("renderNotFound with empty issue ID still renders heading and back link"):
  val html = WorktreeDetailView.renderNotFound("").render

  assert(html.contains("Worktree Not Found"), "Should still show not-found heading for empty ID")
  assert(html.contains("href=\"/\""), "Should still link back to overview")

test("renderNotFound includes 'Back to Projects Overview' link text"):
  val html = WorktreeDetailView.renderNotFound("IW-999").render

  assert(html.contains("Back to Projects Overview"), "Should include link text for overview navigation")

test("renderNotFound does not contain worktree data section CSS classes"):
  val html = WorktreeDetailView.renderNotFound("IW-999").render

  assert(!html.contains("git-status"), "Not-found page should not have git status section")
  assert(!html.contains("pr-link"), "Not-found page should not have PR section")
  assert(!html.contains("progress-bar"), "Not-found page should not have progress bar")
  assert(!html.contains("phase-info"), "Not-found page should not have phase info")
  assert(!html.contains("zed-link"), "Not-found page should not have Zed editor link")
```

</details>

<details>
<summary>CaskServerTest.scala - new integration test (lines 1115-1138)</summary>

```scala
test("GET /worktrees with URL-encoded special characters returns 404 with escaped content"):
  val statePath = createTempStatePath()
  val port = findAvailablePort()

  try
    val serverThread = startTestServer(statePath, port)

    // URL-encode <script>alert(1)</script> so it arrives as a path parameter
    val response = quickRequest
      .get(uri"http://localhost:$port/worktrees/%3Cscript%3Ealert(1)%3C%2Fscript%3E")
      .send()

    assertEquals(response.code.code, 404)
    val html = response.body
    assert(!html.contains("<script>alert"), "Response should not contain unescaped script tag")
    assert(html.contains("not registered") || html.contains("Not Found"),
      "Response should contain a not-found message")
    assert(html.contains("worktree-detail"), "Response should render the styled 404 view")
  ...
```

</details>

<details>
<summary>dashboard-dev-mode.bats - new E2E test (lines 128-176)</summary>

```bash
@test "GET /worktrees/NONEXISTENT-999 returns not-found page" {
    # Starts server with --dev, waits for /health, then:
    HTTP_STATUS=$(curl -s -o /tmp/test-response.txt -w "%{http_code}" \
        "http://localhost:$PORT/worktrees/NONEXISTENT-999")
    RESPONSE=$(cat /tmp/test-response.txt)

    [ "$HTTP_STATUS" -eq 404 ]
    [[ "$RESPONSE" == *"not registered"* ]] || [[ "$RESPONSE" == *"Not Found"* ]]
    [[ "$RESPONSE" == *'href="/"'* ]]
    [[ "$RESPONSE" == *"NONEXISTENT-999"* ]]
}
```

</details>
