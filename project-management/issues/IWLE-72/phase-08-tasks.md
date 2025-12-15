# Phase 8 Tasks: Distribution and Versioning

**Issue:** IWLE-72
**Phase:** 8 - Distribution and versioning
**Created:** 2025-12-15

## Setup

- [ ] [impl] Create release directory structure for artifact packaging

## Config Updates

- [ ] [impl] Add version field to ProjectConfiguration
- [ ] [impl] Update ConfigSerializer to handle version field
- [ ] [impl] Update ConfigFileRepository tests for version field
- [ ] [impl] Make version optional with "latest" default

## Bootstrap Script (thin `iw`)

- [ ] [impl] Create new thin bootstrap script with version detection
- [ ] [impl] Add curl-based download from GitHub releases
- [ ] [impl] Add extraction to ~/.local/share/iw/versions/
- [ ] [impl] Add delegation to iw-run after bootstrap

## Launcher Script (`iw-run`)

- [ ] [impl] Rename/refactor current `iw` logic into `iw-run`
- [ ] [impl] Add --bootstrap command for pre-compilation
- [ ] [impl] Update paths to work from versioned installation directory
- [ ] [impl] Handle PWD argument for project-relative operations

## Release Packaging

- [ ] [impl] Create script to package release tarball
- [ ] [impl] Test tarball extraction and structure
- [ ] [impl] Document release process

## Testing

- [ ] [impl] Test fresh bootstrap from simulated release
- [ ] [impl] Test version switching
- [ ] [impl] Test offline operation after bootstrap
- [ ] [impl] Update E2E tests to work with new structure

## Documentation

- [ ] [impl] Update README with installation instructions
- [ ] [impl] Document version pinning in config
