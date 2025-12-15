# Phase 8 Tasks: Distribution and Versioning

**Issue:** IWLE-72
**Phase:** 8 - Distribution and versioning
**Created:** 2025-12-15

## Setup

- [x] [impl] [ ] [reviewed] Create release directory structure for artifact packaging

## Config Updates

- [x] [impl] [ ] [reviewed] Add version field to ProjectConfiguration
- [x] [impl] [ ] [reviewed] Update ConfigSerializer to handle version field
- [x] [impl] [ ] [reviewed] Update ConfigFileRepository tests for version field
- [x] [impl] [ ] [reviewed] Make version optional with "latest" default

## Bootstrap Script (thin `iw`)

- [x] [impl] [ ] [reviewed] Create new thin bootstrap script with version detection
- [x] [impl] [ ] [reviewed] Add curl-based download from GitHub releases
- [x] [impl] [ ] [reviewed] Add extraction to ~/.local/share/iw/versions/
- [x] [impl] [ ] [reviewed] Add delegation to iw-run after bootstrap

## Launcher Script (`iw-run`)

- [x] [impl] [ ] [reviewed] Rename/refactor current `iw` logic into `iw-run`
- [x] [impl] [ ] [reviewed] Add --bootstrap command for pre-compilation
- [x] [impl] [ ] [reviewed] Update paths to work from versioned installation directory
- [x] [impl] [ ] [reviewed] Handle PWD argument for project-relative operations

## Release Packaging

- [x] [impl] [ ] [reviewed] Create script to package release tarball
- [x] [impl] [ ] [reviewed] Test tarball extraction and structure
- [x] [impl] [ ] [reviewed] Document release process

## Testing

- [x] [impl] [ ] [reviewed] Test fresh bootstrap from simulated release
- [x] [impl] [ ] [reviewed] Test version switching
- [x] [impl] [ ] [reviewed] Test offline operation after bootstrap
- [x] [impl] [ ] [reviewed] Update E2E tests to work with new structure (verified existing tests pass with backward-compatible approach)

## Documentation

- [x] [impl] [ ] [reviewed] Update README with installation instructions
- [x] [impl] [ ] [reviewed] Document version pinning in config
