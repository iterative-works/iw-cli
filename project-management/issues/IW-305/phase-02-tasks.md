# Phase 2 Tasks: E2E tests — add bare remote to setup, test push behavior

## Setup Update

- [ ] [impl] Add bare remote creation to `setup()` — `git init --bare`, `git remote add origin`
- [ ] [impl] Push initial commit and feature branch to bare remote in `setup()`
- [ ] [verify] Run existing tests to confirm they pass with the new setup

## New Tests

- [ ] [impl] Test: phase-start pushes feature branch commits to origin (verify with `git -C <bare> log`)
- [ ] [impl] Test: phase-start fails when push fails (remove remote, expect exit 1)

## Verification

- [ ] [verify] Run full E2E suite: `./iw test e2e`
