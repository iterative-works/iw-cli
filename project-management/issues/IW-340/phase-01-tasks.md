# Phase 1 Tasks: Add --commit flag to review-state commands

## Setup

- [ ] [setup] Read existing update.scala, write.scala, and GitAdapter.commitFileWithRetry
- [ ] [setup] Read existing test/review-state.bats for test patterns

## Tests (TDD — write failing tests first)

- [ ] [test] Add BATS test: update --commit stages and commits review-state.json (git init temp dir, seed file, verify clean tree)
- [ ] [test] Add BATS test: update --commit message contains issue ID and status
- [ ] [test] Add BATS test: update --commit without --status uses generic commit message
- [ ] [test] Add BATS test: update without --commit leaves file uncommitted (dirty tree)
- [ ] [test] Add BATS test: update --help includes --commit flag
- [ ] [test] Add BATS test: write --commit stages and commits the written file (clean tree)
- [ ] [test] Add BATS test: write without --commit leaves file uncommitted
- [ ] [test] Add BATS test: write --help includes --commit flag

## Implementation

- [ ] [impl] Add --commit flag to update.scala ARGS comment
- [ ] [impl] Add --commit detection and post-write commit logic to update.scala
- [ ] [impl] Add --commit to update.scala showHelp()
- [ ] [impl] Add --commit flag to write.scala ARGS comment
- [ ] [impl] Add --commit detection and post-write commit logic to write.scala (both handleFlags and handleStdin paths)
- [ ] [impl] Add --commit to write.scala showHelp()

## Verification

- [ ] [verify] Run all E2E tests: ./iw ./test e2e
- [ ] [verify] Run compile check: scala-cli compile --scalac-option -Werror core/
- [ ] [verify] Verify existing tests still pass
