# Phase 1 Tasks: Move MarkdownTaskParser to model

**Issue:** IW-275
**Phase:** 1 of 3

## Setup

- [x] [setup] Copy `.iw/core/dashboard/MarkdownTaskParser.scala` to `.iw/core/model/MarkdownTaskParser.scala`
- [x] [setup] In `.iw/core/model/MarkdownTaskParser.scala`, change `package iw.core.dashboard` to `package iw.core.model`
- [x] [setup] Delete `.iw/core/dashboard/MarkdownTaskParser.scala`

## Tests

- [x] [test] In `.iw/core/test/MarkdownTaskParserTest.scala` line 8, change `import iw.core.dashboard.MarkdownTaskParser` to `import iw.core.model.MarkdownTaskParser`
- [x] [test] In `.iw/core/test/MarkdownTaskParserTest.scala` line 9, change `import iw.core.dashboard.PhaseIndexEntry` to `import iw.core.model.PhaseIndexEntry`
- [x] [test] In `.iw/core/test/WorkflowProgressServiceTest.scala` line 8, change `import iw.core.dashboard.{WorkflowProgressService, PhaseIndexEntry}` to `import iw.core.dashboard.WorkflowProgressService` and `import iw.core.model.PhaseIndexEntry`

## Implementation

- [x] [impl] In `.iw/core/dashboard/WorkflowProgressService.scala`, add `MarkdownTaskParser, TaskCount, PhaseIndexEntry` to the existing `import iw.core.model.{PhaseInfo, WorkflowProgress, CachedProgress}` on line 6 (these types were previously resolved from the same `dashboard` package without explicit imports; after the move they need explicit imports from `model`)

## Integration

- [x] [integration] Run `scala-cli compile --scalac-option -Werror .iw/core/` and verify no errors or warnings
- [x] [integration] Run `./iw test unit` and verify all tests pass (especially `MarkdownTaskParserTest` and `WorkflowProgressServiceTest`)
- [x] [integration] Run `./iw test e2e` and verify no downstream breakage
- [x] [integration] Commit all changes in a single commit
