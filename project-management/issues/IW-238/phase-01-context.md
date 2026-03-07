# Phase 1 Context: Domain Layer

**Issue:** IW-238
**Phase:** 1 of 3
**Estimated Effort:** 4-6 hours

## Purpose

Implement the pure domain layer for the phase lifecycle commands. All components in this phase are pure functions and value objects with no I/O dependencies. They form the foundation that the Infrastructure (Phase 2) and Presentation (Phase 3) layers build upon.

## Goals

1. Create `PhaseBranch` value object for deriving phase sub-branch names from feature branches
2. Create `CommitMessage` for constructing structured commit message strings
3. Create `PhaseTaskFile` for parsing and rewriting phase task markdown files (updating Phase Status line, updating `[reviewed]` checkboxes)
4. Create `PhaseOutput` data types for the JSON output structures of each command

## Scope

**In Scope:**
- Pure value objects and data types in `.iw/core/model/`
- Pure functions operating on strings and data structures (no filesystem, no git, no shell)
- Unit tests for all components using munit (no mocking needed)

**Out of Scope:**
- Git operations (Phase 2: `GitAdapter` extensions)
- GitHub/GitLab PR/MR creation (Phase 2: `GitHubClient`/`GitLabClient` extensions)
- `ReviewStateAdapter` shared adapter (Phase 2)
- `FileUrlBuilder` for browseable URLs (Phase 2)
- Command scripts `phase-start.scala`, `phase-commit.scala`, `phase-pr.scala` (Phase 3)
- E2E/BATS tests (Phase 3)

## Dependencies

**From Prior Phases:** None (this is the first phase).

**From Existing Codebase:**
- `iw.core.model.IssueId` -- used by `PhaseOutput` to represent issue identifiers
- `ujson` (via upickle dependency) -- used by `PhaseOutput` for JSON serialization
- `munit` -- test framework

## Component Specifications

---

### 1. `PhaseBranch`

**File:** `.iw/core/model/PhaseBranch.scala`

**What it does:** Value object that derives a phase sub-branch name from a feature branch name and a phase number. The sub-branch naming convention is `{featureBranch}-phase-{NN}` where `NN` is a zero-padded two-digit phase number.

**API:**

```scala
package iw.core.model

opaque type PhaseNumber = String

object PhaseNumber:
  /** Parse and validate a phase number string.
    * Accepts: "01", "1", "12" -- always normalizes to zero-padded two digits.
    * Rejects: "0", "100", "-1", "abc", ""
    */
  def parse(raw: String): Either[String, PhaseNumber]

  extension (pn: PhaseNumber)
    def value: String        // zero-padded, e.g. "01"
    def toInt: Int           // integer value, e.g. 1

case class PhaseBranch(featureBranch: String, phaseNumber: PhaseNumber):
  /** Full sub-branch name: "{featureBranch}-phase-{NN}" */
  def branchName: String
```

**Edge Cases:**
- Phase number "0" should be rejected (phases are 1-based)
- Phase numbers above 99 should be rejected (two-digit format)
- Single-digit input "3" should normalize to "03"
- Already zero-padded input "03" should remain "03"
- Feature branch containing "-phase-" already (e.g., re-running) -- no special handling needed; the result would be `branch-phase-01-phase-02` which is intentional if the user does it
- Empty feature branch string should be rejected

**Test File:** `.iw/core/test/PhaseBranchTest.scala`

---

### 2. `CommitMessage`

**File:** `.iw/core/model/CommitMessage.scala`

**What it does:** Pure construction of structured commit messages. Produces a message with a title line and optional bulleted list of items.

**API:**

```scala
package iw.core.model

object CommitMessage:
  /** Build a commit message from a title and optional list of items.
    *
    * Format (title only):
    *   {title}
    *
    * Format (with items):
    *   {title}
    *
    *   - {item1}
    *   - {item2}
    */
  def build(title: String, items: List[String] = Nil): String
```

**Edge Cases:**
- Empty title should return empty string (or the caller validates; keep it simple -- just return what's given)
- Empty items list produces title-only message (no blank line, no bullets)
- Items with leading/trailing whitespace should be trimmed
- Single item produces one bullet line
- Title with trailing newline should be trimmed

**Test File:** `.iw/core/test/CommitMessageTest.scala`

---

### 3. `PhaseTaskFile`

**File:** `.iw/core/model/PhaseTaskFile.scala`

**What it does:** Pure functions for parsing and rewriting phase task markdown content. Two operations:
1. Update the "Phase Status" line to mark a phase as complete
2. Update `[reviewed]` checkboxes based on `[impl]` checkbox state

**Observed Phase Task File Format (from real files in this repository):**

The format has natural variation, but the key patterns are:

- **Phase Status line** appears at or near the end of the file. Observed formats:
  - `**Phase Status:** Complete`
  - `**Phase Status:** Complete ✓`
  - `**Phase Status:** Not Started`
  - `**Phase Status:** Ready for Implementation`
  - `**Phase Status:** Implementation Complete - Ready for Code Review`
  - `## Phase Status: Complete`

  The canonical form used by most files is `**Phase Status:** {value}` (bold label with colon, then value).

- **Checkbox lines with tags** use inline tag markers. The core patterns:
  - `- [x] [impl] Description` -- impl task completed, no review tracking
  - `- [ ] [impl] Description` -- impl task not started
  - `- [x] [impl] [x] [reviewed] Description` -- impl done AND reviewed
  - `- [x] [impl] [ ] [reviewed] Description` -- impl done, NOT YET reviewed
  - `- [ ] [impl] [ ] [reviewed] Description` -- neither done
  - `- [x] [test] [x] [reviewed] Description` -- test task with review tracking
  - `- [x] [setup] Description` -- setup task (may not have `[reviewed]`)
  - `- [x] [int] Description` -- integration task

  Not all task lines have `[reviewed]` markers. The `[reviewed]` marker is only present on lines that were created with it. The `phase-commit` command should only update `[reviewed]` checkboxes on lines that already have a `[reviewed]` marker.

**API:**

```scala
package iw.core.model

object PhaseTaskFile:
  /** Update the Phase Status line to "Complete".
    *
    * Finds the line matching `**Phase Status:**` (bold format) and replaces
    * the value with "Complete". If no such line exists, appends
    * `**Phase Status:** Complete` at the end.
    *
    * @param content Full markdown file content
    * @return Updated content with Phase Status set to Complete
    */
  def markComplete(content: String): String

  /** Mark all checked [impl] tasks as [reviewed] where a [reviewed] marker exists.
    *
    * For lines matching `- [x] [impl] [ ] [reviewed]` or
    * `- [x] [test] [ ] [reviewed]` (any tag before [reviewed]),
    * change `[ ] [reviewed]` to `[x] [reviewed]`.
    *
    * Only touches lines that already have a `[reviewed]` marker.
    * Lines without `[reviewed]` are left unchanged.
    * Lines where the primary checkbox is unchecked `- [ ]` are left unchanged.
    *
    * @param content Full markdown file content
    * @return Updated content with reviewed checkboxes marked
    */
  def markReviewed(content: String): String
```

**Edge Cases:**
- File with no "Phase Status" line -- `markComplete` should append one
- File with "Phase Status" already set to "Complete" -- should be idempotent
- File with `## Phase Status:` (heading format) vs `**Phase Status:**` (bold format) -- handle the bold format (canonical); heading format can be left as-is (it is rare)
- Lines with `- [x] [impl]` but no `[reviewed]` marker -- leave unchanged
- Lines with `- [ ] [impl] [ ] [reviewed]` (impl NOT done) -- leave `[reviewed]` unchecked
- Lines with `- [x] [impl] [x] [reviewed]` (already reviewed) -- idempotent, leave as-is
- Mixed tags: `- [x] [test] [ ] [reviewed]` should also get reviewed
- Non-checkbox lines should be preserved exactly
- Whitespace and indentation should be preserved

**Test File:** `.iw/core/test/PhaseTaskFileTest.scala`

**Test Data:** Create sample phase task markdown strings inline in tests (no separate resource files needed for pure string transformation tests).

---

### 4. `PhaseOutput`

**File:** `.iw/core/model/PhaseOutput.scala`

**What it does:** Data types (case classes) representing the JSON output structures for each of the three phase commands. Includes a `toJson` method for serializing to JSON strings using `ujson`.

**API:**

```scala
package iw.core.model

object PhaseOutput:

  /** Output of `phase-start` command */
  case class StartOutput(
    issueId: String,
    phaseNumber: String,
    branch: String,
    baselineSha: String
  ):
    def toJson: String

  /** Output of `phase-commit` command */
  case class CommitOutput(
    issueId: String,
    phaseNumber: String,
    commitSha: String,
    filesCommitted: Int,
    message: String
  ):
    def toJson: String

  /** Output of `phase-pr` command */
  case class PrOutput(
    issueId: String,
    phaseNumber: String,
    prUrl: String,
    headBranch: String,
    baseBranch: String,
    merged: Boolean
  ):
    def toJson: String
```

**Edge Cases:**
- JSON output must be pretty-printed (indented) for readability, consistent with `ReviewStateBuilder.build()` which uses `ujson.write(obj, indent = 2)`
- All string fields should be properly escaped in JSON (ujson handles this)
- `merged` boolean should serialize as JSON `true`/`false`
- Empty string fields should serialize as `""`, not be omitted

**Test File:** `.iw/core/test/PhaseOutputTest.scala`

---

## File Locations Summary

| File | Type | Description |
|------|------|-------------|
| `.iw/core/model/PhaseBranch.scala` | New | Phase branch name value object + PhaseNumber opaque type |
| `.iw/core/model/CommitMessage.scala` | New | Commit message construction |
| `.iw/core/model/PhaseTaskFile.scala` | New | Phase task markdown parsing and rewriting |
| `.iw/core/model/PhaseOutput.scala` | New | JSON output data types for phase commands |
| `.iw/core/test/PhaseBranchTest.scala` | New | Unit tests for PhaseBranch and PhaseNumber |
| `.iw/core/test/CommitMessageTest.scala` | New | Unit tests for CommitMessage |
| `.iw/core/test/PhaseTaskFileTest.scala` | New | Unit tests for PhaseTaskFile |
| `.iw/core/test/PhaseOutputTest.scala` | New | Unit tests for PhaseOutput |

No existing files need to be modified in this phase.

## Testing Strategy

All components are pure functions (string in, string out; data in, data out). Testing is straightforward:

- **Framework:** munit `FunSuite` (matches existing test conventions)
- **Package:** Tests use `iw.core.domain` or `iw.tests` package (both conventions exist; prefer `iw.core.domain`)
- **Pattern:** Follow the existing test style seen in `PhaseInfoTest.scala` and `IssueIdTest.scala`:
  - Simple `test("description"):` blocks
  - `assertEquals` for value comparisons
  - `assert(result.isRight)` / `assert(result.isLeft)` for Either results
- **No mocking:** All domain logic is pure. Tests pass data in and assert on data out.
- **Test data:** Inline markdown strings in tests (no resource files needed for Phase 1)
- **Run command:** `./iw test unit`

## Acceptance Criteria

### Functional
- [ ] `PhaseNumber.parse("3")` returns `Right` with value "03"
- [ ] `PhaseNumber.parse("0")` returns `Left` with error message
- [ ] `PhaseBranch("IW-238", phaseNum).branchName` returns `"IW-238-phase-01"`
- [ ] `CommitMessage.build("title", List("a", "b"))` returns properly formatted multi-line string
- [ ] `CommitMessage.build("title")` returns just the title
- [ ] `PhaseTaskFile.markComplete(content)` updates `**Phase Status:**` line to "Complete"
- [ ] `PhaseTaskFile.markComplete(content)` appends status line if none exists
- [ ] `PhaseTaskFile.markReviewed(content)` marks `[ ] [reviewed]` as `[x] [reviewed]` where the primary checkbox is checked
- [ ] `PhaseTaskFile.markReviewed(content)` does not touch lines without `[reviewed]` markers
- [ ] `PhaseTaskFile.markReviewed(content)` does not touch lines where the primary checkbox is unchecked
- [ ] `PhaseOutput.StartOutput(...).toJson` produces valid, pretty-printed JSON
- [ ] `PhaseOutput.CommitOutput(...).toJson` produces valid JSON with correct field names
- [ ] `PhaseOutput.PrOutput(...).toJson` produces valid JSON with boolean `merged` field

### Technical
- [ ] All new files have PURPOSE comments (two lines starting with `// PURPOSE:`)
- [ ] All new files are in `iw.core.model` package
- [ ] All code is pure (no I/O, no side effects, no mutable state)
- [ ] All functions return values (no `Unit` returns except test bodies)
- [ ] Code matches existing style conventions (Scala 3, indentation-based syntax)

### Testing
- [ ] All unit tests pass: `./iw test unit`
- [ ] Full test suite passes: `./iw test` (no regressions)
- [ ] Tests cover happy paths and edge cases documented above
- [ ] Tests use inline test data, no mocking
