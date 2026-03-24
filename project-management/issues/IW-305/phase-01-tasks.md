# Phase 1 Tasks: Command fix — push feature branch in phase-start

## Implementation

- [x] [impl] Add `GitAdapter.push(featureBranch, os.pwd, setUpstream = true)` call before `createAndCheckoutBranch` in `phase-start.scala`
- [x] [impl] Wrap push call with `CommandHelpers.exitOnError` so phase-start fails fast on push error
- [x] [verify] Confirm `phase-start.scala` compiles cleanly with `scala-cli compile`
