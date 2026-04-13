# Phase 1 Tasks: Add --commit flag to review-state commands

## Setup

- [x] [setup] Read existing update.scala, write.scala, and GitAdapter.commitFileWithRetry
- [x] [setup] Read existing test/review-state.bats for test patterns

## Tests (TDD — write failing tests first)

- [x] [test] Add BATS test: update --commit stages and commits review-state.json (git init temp dir, seed file, verify clean tree)
- [x] [test] Add BATS test: update --commit message contains issue ID and status
- [x] [test] Add BATS test: update --commit without --status uses generic commit message
- [x] [test] Add BATS test: update without --commit leaves file uncommitted (dirty tree)
- [x] [test] Add BATS test: update --help includes --commit flag
- [x] [test] Add BATS test: write --commit stages and commits the written file (clean tree)
- [x] [test] Add BATS test: write without --commit leaves file uncommitted
- [x] [test] Add BATS test: write --help includes --commit flag

## Implementation

- [x] [impl] Add --commit flag to update.scala ARGS comment
- [x] [impl] Add --commit detection and post-write commit logic to update.scala
- [x] [impl] Add --commit to update.scala showHelp()
- [x] [impl] Add --commit flag to write.scala ARGS comment
- [x] [impl] Add --commit detection and post-write commit logic to write.scala (both handleFlags and handleStdin paths)
- [x] [impl] Add --commit to write.scala showHelp()

## Verification

- [x] [verify] Run all E2E tests: ./iw ./test e2e
- [x] [verify] Run compile check: scala-cli compile --scalac-option -Werror core/
- [x] [verify] Verify existing tests still pass
**Phase Status:** Complete
