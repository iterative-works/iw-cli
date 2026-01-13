# Story-Driven Analysis: Open issue folder in Zed editor

**Issue:** IW-74
**Created:** 2026-01-13
**Status:** Draft
**Classification:** Simple

## Problem Statement

Users currently interact with their worktree folders through tmux sessions and command-line operations. When they want to edit files using a graphical code editor like Zed, they must manually navigate to the folder or open it from within the terminal.

This creates friction in the workflow. Users viewing the dashboard can see their worktrees and click links to issue trackers or PR pages, but cannot directly launch their preferred editor to work on code. Adding a "Open in Zed" button to each worktree card on the dashboard would streamline the transition from reviewing issues to actively coding.

The value is reduced context switching and faster entry into the development flow. Users can go from dashboard overview to coding in Zed with a single click.

## User Stories

### Story 1: Open worktree folder in Zed from dashboard

```gherkin
Feature: Open worktree in Zed editor
  As a developer viewing the dashboard
  I want to click a button to open the worktree folder in Zed
  So that I can quickly start editing code without manual navigation

Scenario: Successfully open existing worktree in Zed
  Given a worktree card is displayed on the dashboard for issue "IW-74"
  And the worktree folder exists at the registered path
  And Zed editor is installed on the system
  When I click the "Open in Zed" button
  Then Zed launches with the worktree folder opened
  And I can immediately start editing files in the editor
```

**Estimated Effort:** 3-4h
**Complexity:** Straightforward

**Technical Feasibility:**
This is straightforward because the dashboard infrastructure already supports launching external applications (browser opening via platform-specific commands). The Zed CLI (`zed`) is well-documented and accepts directory paths as arguments. Platform detection logic already exists in `dashboard.scala`.

Key technical aspects:
- Zed provides a CLI command (`zed <path>`) that opens folders
- Dashboard already has platform detection (macOS/Linux/Windows)
- Worktree cards already render with actions (PR links, artifact links)
- ScalaTags can easily add button elements with HTMX attributes

**Acceptance:**
- Worktree card displays "Open in Zed" button next to other actions
- Clicking button launches Zed with correct folder path
- Works on macOS and Linux (primary development platforms)
- Button is disabled/hidden if Zed is not installed
- No error dialogs on dashboard if Zed fails to launch (graceful degradation)

---

### Story 2: Handle Zed not installed gracefully

```gherkin
Feature: Graceful handling when Zed is unavailable
  As a developer without Zed installed
  I want the dashboard to handle the absence of Zed gracefully
  So that I don't encounter errors or broken functionality

Scenario: Zed CLI not available on system
  Given a worktree card is displayed on the dashboard
  And Zed editor is not installed or not in PATH
  When the dashboard renders the worktree card
  Then the "Open in Zed" button is not displayed
  And no errors are logged to the console
```

**Estimated Effort:** 1-2h
**Complexity:** Straightforward

**Technical Feasibility:**
Simple because `CommandRunner` already has `isCommandAvailable` for tool detection (used for gh/glab detection in PR features). We can use the same pattern to check if `zed` command is available before rendering the button.

**Acceptance:**
- Button only appears if `zed` command is found in PATH
- Tool detection happens during dashboard rendering
- No performance impact from detection (cached per render cycle)
- Users without Zed simply don't see the button

---

### Story 3: Handle launch errors gracefully

```gherkin
Feature: Graceful error handling for Zed launch failures
  As a developer using the dashboard
  I want errors launching Zed to be handled gracefully
  So that I can understand what went wrong without breaking the dashboard

Scenario: Zed command exists but fails to launch
  Given a worktree card displays "Open in Zed" button
  And Zed CLI is available but encounters an error (permissions, path issues, etc.)
  When I click the "Open in Zed" button
  Then the dashboard logs the error to server console for debugging
  And the user sees a non-blocking notification or the action silently fails
  And the dashboard remains functional
```

**Estimated Effort:** 1-2h
**Complexity:** Straightforward

**Technical Feasibility:**
Similar to browser opening in `dashboard.scala` which uses best-effort process launching. Process launch errors are caught and logged without disrupting the UI. We follow the same pattern.

**Acceptance:**
- Launch failures don't crash the server or break the dashboard
- Errors are logged to server stderr for troubleshooting
- User experience degrades gracefully (button doesn't respond, or shows momentary feedback)
- Dashboard remains interactive after failures

## Architectural Sketch

**Purpose:** List WHAT components each story needs, not HOW they're implemented.

### For Story 1: Open worktree in Zed from dashboard

**Presentation Layer:**
- Button element in `WorktreeListView.scala` worktree card rendering
- HTMX endpoint or direct link to trigger Zed launch
- CSS styling for "Open in Zed" button (match existing PR button style)

**Infrastructure Layer:**
- HTTP route in `CaskServer.scala` to handle Zed launch request (e.g., `GET /api/worktrees/:issueId/open-zed`)
- Platform detection function (can reuse `openBrowser` pattern from `dashboard.scala`)
- Command execution wrapper (use `CommandRunner` or `ProcessBuilder`)

**Domain Layer:**
- No new domain objects needed (reuse existing `WorktreeRegistration`)

---

### For Story 2: Handle Zed not installed gracefully

**Presentation Layer:**
- Conditional rendering logic in `WorktreeListView.scala` (show/hide button based on availability)

**Infrastructure Layer:**
- Tool detection function (reuse `CommandRunner.isCommandAvailable` pattern)
- Cache detection result during dashboard render cycle to avoid repeated checks

**Application Layer:**
- Service function in `DashboardService.scala` to check Zed availability once per render

---

### For Story 3: Handle launch errors gracefully

**Infrastructure Layer:**
- Error handling in Zed launch route (wrap `ProcessBuilder.start()` with Try/Either)
- Logging to stderr for debugging
- HTTP response indicating success/failure (200 OK or best-effort 202 Accepted)

**Presentation Layer:**
- No UI changes needed (graceful degradation means silent failure or console log)

## Technical Risks & Uncertainties

### CLARIFY: Zed URL scheme vs CLI command

The issue description mentions investigating `zed://` URL scheme. However, Zed also provides a CLI command `zed <path>`.

**Questions to answer:**
1. Should we use `zed://` URL scheme or `zed <path>` CLI command?
2. Does `zed://` scheme work cross-platform (macOS, Linux, Windows)?
3. Which approach is more reliable and easier to implement?

**Options:**
- **Option A: Use CLI command (`zed <path>`)**
  - Pros: Simple, direct, works like existing browser launch pattern
  - Cons: Requires Zed CLI to be in PATH
  - Implementation: `ProcessBuilder` with `zed <worktree-path>`

- **Option B: Use URL scheme (`zed://file/<path>`)**
  - Pros: Might work without CLI in PATH, browser-like UX
  - Cons: URL scheme format unclear, may require encoding, platform differences
  - Implementation: Use platform launcher (xdg-open, open) with `zed://` URL

- **Option C: Try URL scheme first, fallback to CLI**
  - Pros: Maximum compatibility
  - Cons: More complex, harder to debug
  - Implementation: Detect which works and prefer that

**Impact:** Affects Stories 1 and 2. URL scheme detection would require different availability checking. CLI command is simpler and more predictable.

**Recommendation:** Start with Option A (CLI command) as it matches existing patterns in the codebase and is well-documented in Zed documentation. URL scheme can be added later if needed.

---

### CLARIFY: Button placement in worktree card

Currently, worktree cards show:
- Issue title and ID link
- Git status
- PR link (if available)
- Phase progress
- Review artifacts
- Last activity

**Questions to answer:**
1. Where should the "Open in Zed" button appear in the card layout?
2. Should it be next to the PR button, or in a separate section?
3. Should there be multiple editor buttons (VS Code, IntelliJ, etc.) in the future?

**Options:**
- **Option A: Button next to PR link** (horizontal layout)
  - Pros: Groups all action buttons together
  - Cons: May get crowded if we add more editors later

- **Option B: Separate "Actions" section** at top or bottom of card
  - Pros: Scalable for multiple editor types, clear separation of concerns
  - Cons: Takes more vertical space

- **Option C: Dropdown/menu for editors** (if supporting multiple editors)
  - Pros: Compact, extensible
  - Cons: Requires dropdown UI, more complex

**Impact:** Affects Story 1 UI design. Does not block implementation but affects maintainability if more editors are added.

**Recommendation:** Option A for now (button next to PR link) since we only support Zed. If more editors are requested later, refactor to Option B or C.

---

### CLARIFY: Scope of Zed-specific features

This issue is titled "Open issue folder in Zed editor." However, the pattern could apply to other editors.

**Questions to answer:**
1. Is this specifically for Zed, or should we design for multiple editors from the start?
2. Should there be a user preference or configuration for default editor?
3. Should we support editor-specific features (e.g., opening specific files, not just folders)?

**Options:**
- **Option A: Zed-only for now** (simplest)
  - Pros: Focused scope, matches issue title, ships faster
  - Cons: Might need refactoring if other editors requested

- **Option B: Generic "Open in Editor" with config**
  - Pros: More flexible, supports VS Code, IntelliJ, etc.
  - Cons: More complex, requires configuration, out of scope for IW-74

- **Option C: Hardcode Zed now, design for extensibility**
  - Pros: Ships fast, easy to add editors later
  - Cons: Middle ground, might over-engineer

**Impact:** Affects all stories. Option A simplifies Stories 1-3 significantly. Options B/C require configuration layer and abstraction.

**Recommendation:** Option A (Zed-only) to match issue scope. Future issues can add other editors following the same pattern.

## Total Estimates

**Story Breakdown:**
- Story 1 (Open worktree in Zed from dashboard): 3-4 hours
- Story 2 (Handle Zed not installed gracefully): 1-2 hours
- Story 3 (Handle launch errors gracefully): 1-2 hours

**Total Range:** 5-8 hours

**Confidence:** High

**Reasoning:**
- Well-understood problem with existing patterns in codebase
- Similar functionality already exists (browser opening, platform detection, tool detection)
- Minimal new infrastructure needed (reuse CommandRunner, ProcessBuilder patterns)
- No complex domain logic or state management required
- Straightforward UI changes (one button per card)
- Main uncertainty is CLARIFY #1 (URL scheme vs CLI), but CLI is low-risk default
- Testing approach is clear (E2E via BATS, unit tests for detection logic)

## Testing Approach

**Per Story Testing:**

Each story should have:
1. **Unit Tests**: Pure logic (platform detection, tool availability detection)
2. **Integration Tests**: Command execution with mocked ProcessBuilder
3. **E2E Scenario Tests**: Automated verification of the Gherkin scenario (browser-based)

**Story-Specific Testing Notes:**

**Story 1:**
- Unit: Test button rendering logic (with/without Zed available)
- Integration: Test Zed launch command construction per platform
- E2E: Visit dashboard, verify button appears, click button (verify process launched - check logs or mock)

**Story 2:**
- Unit: Test `isZedAvailable` function with mocked command checks
- Integration: Test dashboard rendering with Zed unavailable
- E2E: Run dashboard on system without Zed, verify no button appears, no errors logged

**Story 3:**
- Unit: Test error handling logic (Try/Either patterns)
- Integration: Test route handler with failing process launch
- E2E: Trigger Zed launch with invalid path or broken Zed installation, verify dashboard remains functional

**Test Data Strategy:**
- Use test worktrees with known paths
- Mock `CommandRunner.isCommandAvailable` for Zed detection
- Mock `ProcessBuilder` for command execution in unit/integration tests
- E2E tests can use real Zed installation (optional, skip if not installed)

**Regression Coverage:**
- Ensure existing worktree card features still work (PR links, artifacts, etc.)
- Verify dashboard rendering performance (button detection should not slow down page load)
- Check that other dashboard routes are unaffected

## Deployment Considerations

### Database Changes
None required. No schema changes needed (uses existing `WorktreeRegistration` data).

### Configuration Changes
None required. No environment variables or config files needed (Zed detection is automatic).

**Optional:** Could add future configuration for default editor preference, but not needed for IW-74.

### Rollout Strategy
Simple deployment:
1. Deploy updated code (new button in UI, new route in CaskServer)
2. Restart dashboard server
3. Users with Zed installed immediately see button
4. Users without Zed see no change

No feature flags needed (graceful degradation handles availability).

### Rollback Plan
If issues arise:
1. Revert code to previous version
2. Restart dashboard
3. Button disappears, no data loss or state corruption (stateless feature)

## Dependencies

### Prerequisites
- Dashboard server must be running (existing requirement)
- Worktrees must be registered (existing requirement)
- Zed CLI must be installed and in PATH (for feature to work, not required for deployment)

### Story Dependencies
- Story 2 depends on Story 1 (detection logic needs button rendering logic)
- Story 3 depends on Story 1 (error handling needs launch logic)
- Stories can be implemented sequentially or Story 2+3 can be combined with Story 1

### External Blockers
None. All dependencies are within the iw-cli codebase.

---

## Implementation Sequence

**Recommended Story Order:**

1. **Story 1: Open worktree in Zed from dashboard** - Establishes foundation, delivers core value
2. **Story 2: Handle Zed not installed gracefully** - Adds robustness, prevents confusion for users without Zed
3. **Story 3: Handle launch errors gracefully** - Polishes UX, ensures reliability

**Iteration Plan:**

- **Iteration 1** (Story 1): Core functionality - button renders, Zed launches (3-4h)
- **Iteration 2** (Stories 2-3): Robustness - detection and error handling (2-4h)

**Alternative:** All three stories can be implemented together in a single 5-8h session since they're tightly coupled.

## Documentation Requirements

- [ ] Update dashboard documentation (if exists) to mention "Open in Zed" feature
- [ ] Add comment in `WorktreeListView.scala` explaining button rendering logic
- [ ] Add comment in new route explaining Zed launch mechanism
- [ ] Optional: Add tip in README about Zed CLI installation

---

**Analysis Status:** Ready for Review

**Next Steps:**
1. Resolve CLARIFY markers (especially #1: URL scheme vs CLI command)
2. Get approval on button placement (CLARIFY #2)
3. Confirm scope is Zed-only (CLARIFY #3)
4. Run `/iterative-works:ag-create-tasks IW-74` to map stories to implementation phases
5. Run `/iterative-works:ag-implement IW-74` for iterative story-by-story implementation
