# Phase 1 Tasks: Command fix — push feature branch in phase-start

## Implementation

- [ ] [impl] Add `GitAdapter.push(featureBranch, os.pwd, setUpstream = true)` call before `createAndCheckoutBranch` in `phase-start.scala`
- [ ] [impl] Wrap push call with `CommandHelpers.exitOnError` so phase-start fails fast on push error
- [ ] [verify] Confirm `phase-start.scala` compiles cleanly with `scala-cli compile`
