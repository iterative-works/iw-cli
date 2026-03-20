# Phase 1: Move MarkdownTaskParser to model

**Issue:** IW-275
**Phase:** 1 of 3
**Story:** MarkdownTaskParser contains pure functions accessible from both dashboard and commands

## Goals

Move `MarkdownTaskParser.scala` (and its associated types `TaskCount`, `PhaseIndexEntry`) from `iw.core.dashboard` to `iw.core.model`. This is a refactoring prerequisite for Phase 2, which needs to import `PhaseIndexEntry` from `model/` in the new `batch-implement` command. Per the project's FCIS architecture rules, commands must not import from `dashboard/` — only from `model/`, `adapters/`, and `output/`.

## Scope

### In Scope
- Move `MarkdownTaskParser.scala` from `.iw/core/dashboard/` to `.iw/core/model/`
- Change its package declaration from `iw.core.dashboard` to `iw.core.model`
- Update all imports in files that reference `MarkdownTaskParser`, `TaskCount`, or `PhaseIndexEntry`
- Ensure all tests pass after the move

### Out of Scope
- Adding new functionality to `MarkdownTaskParser`
- Creating `BatchImplement` model (Phase 2)
- Creating the `batch-implement` command (Phase 3)
- Changing any logic or signatures in `MarkdownTaskParser`

## Dependencies

- No prior phases
- IW-274 (activity + workflow_type fields) is already merged

## Approach

1. **Move the file:** Copy `.iw/core/dashboard/MarkdownTaskParser.scala` to `.iw/core/model/MarkdownTaskParser.scala`
2. **Update package declaration:** Change `package iw.core.dashboard` to `package iw.core.model` in the moved file
3. **Update dashboard imports:** In `WorkflowProgressService.scala`, `MarkdownTaskParser` is used directly (same package, no import needed). After the move, it will need an explicit `import iw.core.model.{MarkdownTaskParser, PhaseIndexEntry}` (or the existing wildcard `iw.core.model.*` may cover it, since `WorkflowProgressService` already imports from `iw.core.model`)
4. **Update test imports:** Change imports in `MarkdownTaskParserTest.scala` and `WorkflowProgressServiceTest.scala` from `iw.core.dashboard` to `iw.core.model`
5. **Delete the original file** from `dashboard/`
6. **Run compilation check:** `scala-cli compile --scalac-option -Werror .iw/core/`
7. **Run unit tests:** `./iw test unit`

## Files to Modify

### Move (create new, delete old)
- `.iw/core/dashboard/MarkdownTaskParser.scala` → `.iw/core/model/MarkdownTaskParser.scala`
  - Change: `package iw.core.dashboard` → `package iw.core.model`

### Update imports
- `.iw/core/dashboard/WorkflowProgressService.scala`
  - Currently uses `MarkdownTaskParser` without import (same package). After the move, needs explicit import. The file already has `import iw.core.model.{PhaseInfo, WorkflowProgress, CachedProgress}` — extend this to include `MarkdownTaskParser`, `TaskCount`, and `PhaseIndexEntry` (or use wildcard)
  - The `PhaseIndexEntry` type is used in method signatures (`computeProgress`, `determineCurrentPhase`) — these are currently resolved from the same package. After the move they must come from `iw.core.model`
- `.iw/core/test/MarkdownTaskParserTest.scala`
  - Change: `import iw.core.dashboard.MarkdownTaskParser` → `import iw.core.model.MarkdownTaskParser`
  - Change: `import iw.core.dashboard.PhaseIndexEntry` → `import iw.core.model.PhaseIndexEntry`
- `.iw/core/test/WorkflowProgressServiceTest.scala`
  - Change: `import iw.core.dashboard.{WorkflowProgressService, PhaseIndexEntry}` → `import iw.core.dashboard.WorkflowProgressService` + `import iw.core.model.PhaseIndexEntry`

## Testing Strategy

No new tests are needed. This is a pure move refactoring.

- **Compilation check:** `scala-cli compile --scalac-option -Werror .iw/core/` must pass with no warnings
- **Unit tests:** `./iw test unit` — all existing `MarkdownTaskParserTest` and `WorkflowProgressServiceTest` tests must continue to pass
- **E2E tests:** `./iw test e2e` — run to verify nothing downstream broke

## Acceptance Criteria

- [ ] `MarkdownTaskParser.scala` exists at `.iw/core/model/MarkdownTaskParser.scala` with `package iw.core.model`
- [ ] `MarkdownTaskParser.scala` no longer exists at `.iw/core/dashboard/MarkdownTaskParser.scala`
- [ ] `WorkflowProgressService.scala` compiles with explicit imports from `iw.core.model`
- [ ] `scala-cli compile --scalac-option -Werror .iw/core/` passes
- [ ] All unit tests pass (`./iw test unit`)
- [ ] All E2E tests pass (`./iw test e2e`)
- [ ] Changes committed in a single commit
