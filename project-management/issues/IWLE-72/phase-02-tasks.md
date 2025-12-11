# Phase 2 Tasks: Initialize project with issue tracker configuration

**Issue:** IWLE-72
**Phase:** 2 of 7
**Status:** In Progress

## Setup

- [x] [impl] [ ] [reviewed] Add Typesafe Config dependency to project.scala
- [x] [impl] [ ] [reviewed] Create `.iw/core/Config.scala` with domain model scaffolding

## Tests - Domain Layer

- [x] [impl] [ ] [reviewed] Write tests for GitRemote URL parsing (github.com, gitlab.e-bs.cz, SSH and HTTPS formats)
- [x] [impl] [ ] [reviewed] Write tests for tracker detection from git remote host
- [x] [impl] [ ] [reviewed] Write tests for ProjectConfiguration HOCON serialization/deserialization
- [x] [impl] [ ] [reviewed] Write tests for configuration validation (required fields)

## Implementation - Domain Layer

- [x] [impl] [ ] [reviewed] Implement GitRemote value object with URL parsing
- [x] [impl] [ ] [reviewed] Implement IssueTrackerType enum (Linear, YouTrack)
- [x] [impl] [ ] [reviewed] Implement ProjectConfiguration case class
- [x] [impl] [ ] [reviewed] Implement Configuration HOCON serialization

## Tests - Application Layer

- [x] [impl] [ ] [reviewed] Write tests for TrackerDetector.suggestTracker logic
- [ ] [impl] [ ] [reviewed] Write tests for ConfigurationService validation errors

## Implementation - Application Layer

- [x] [impl] [ ] [reviewed] Implement TrackerDetector service
- [x] [impl] [ ] [reviewed] Implement ConfigurationService with init logic

## Tests - Infrastructure Layer

- [x] [impl] [ ] [reviewed] Write tests for GitAdapter remote URL reading
- [x] [impl] [ ] [reviewed] Write tests for ConfigFileRepository write/read roundtrip

## Implementation - Infrastructure Layer

- [x] [impl] [ ] [reviewed] Implement GitAdapter for git remote operations
- [x] [impl] [ ] [reviewed] Implement ConfigFileRepository for HOCON file I/O
- [x] [impl] [ ] [reviewed] Implement Prompt utility for interactive console input

## Tests - Command Layer

- [ ] [impl] [ ] [reviewed] Write integration test for init command happy path
- [ ] [impl] [ ] [reviewed] Write integration test for init outside git repo
- [ ] [impl] [ ] [reviewed] Write integration test for init when config exists

## Implementation - Command

- [x] [impl] [ ] [reviewed] Implement init.scala command with full workflow
- [x] [impl] [ ] [reviewed] Handle --force flag for config overwrite
- [x] [impl] [ ] [reviewed] Add env var hint messages for Linear/YouTrack

## Verification

- [x] [verify] Run all tests and ensure they pass
- [x] [verify] Manual test: ./iw init in clean git repo
- [x] [verify] Manual test: ./iw init outside git repo (should error)
- [x] [verify] Manual test: ./iw init when config exists (should error, suggest --force)

## Notes

- No API calls in this phase - token validation is Phase 3
- Config file should be committable (no secrets)
- Use HOCON format with Typesafe Config library
