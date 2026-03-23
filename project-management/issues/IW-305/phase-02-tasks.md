# Phase 2 Tasks: E2E tests — add bare remote to setup, test push behavior

## Setup Update

- [x] [impl] Add bare remote creation to `setup()` — `git init --bare`, `git remote add origin`
- [x] [impl] Push initial commit and feature branch to bare remote in `setup()`
- [x] [verify] Run existing tests to confirm they pass with the new setup

## New Tests

- [x] [impl] Test: phase-start pushes feature branch commits to origin (verify with `git -C <bare> log`)
- [x] [impl] Test: phase-start fails when push fails (remove remote, expect exit 1)

## Verification

- [x] [verify] Run phase-start E2E tests: all 10 pass
