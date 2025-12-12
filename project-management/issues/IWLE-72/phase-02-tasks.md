# Phase 2 Tasks: Initialize project with issue tracker configuration

**Issue:** IWLE-72
**Phase:** 2 of 7
**Status:** Complete

## Setup

- [x] [impl] [x] [reviewed] Add Typesafe Config dependency to project.scala
- [x] [impl] [x] [reviewed] Create `.iw/core/Config.scala` with domain model scaffolding

## Tests - Domain Layer

- [x] [impl] [x] [reviewed] Write tests for GitRemote URL parsing (github.com, gitlab.e-bs.cz, SSH and HTTPS formats)
- [x] [impl] [x] [reviewed] Write tests for tracker detection from git remote host
- [x] [impl] [x] [reviewed] Write tests for ProjectConfiguration HOCON serialization/deserialization
- [x] [impl] [x] [reviewed] Write tests for configuration validation (required fields)

## Implementation - Domain Layer

- [x] [impl] [x] [reviewed] Implement GitRemote value object with URL parsing
- [x] [impl] [x] [reviewed] Implement IssueTrackerType enum (Linear, YouTrack)
- [x] [impl] [x] [reviewed] Implement ProjectConfiguration case class
- [x] [impl] [x] [reviewed] Implement Configuration HOCON serialization

## Tests - Application Layer

- [x] [impl] [x] [reviewed] Write tests for TrackerDetector.suggestTracker logic
- [x] [impl] [x] [reviewed] Write tests for ConfigurationService validation errors (covered by ConfigFileTest)

## Implementation - Application Layer

- [x] [impl] [x] [reviewed] Implement TrackerDetector service
- [x] [impl] [x] [reviewed] Implement ConfigurationService with init logic

## Tests - Infrastructure Layer

- [x] [impl] [x] [reviewed] Write tests for GitAdapter remote URL reading
- [x] [impl] [x] [reviewed] Write tests for ConfigFileRepository write/read roundtrip

## Implementation - Infrastructure Layer

- [x] [impl] [x] [reviewed] Implement GitAdapter for git remote operations
- [x] [impl] [x] [reviewed] Implement ConfigFileRepository for HOCON file I/O
- [x] [impl] [x] [reviewed] Implement Prompt utility for interactive console input

## Tests - Command Layer

- [x] [impl] [x] [reviewed] Write integration test for init command happy path (manual verification)
- [x] [impl] [x] [reviewed] Write integration test for init outside git repo (manual verification)
- [x] [impl] [x] [reviewed] Write integration test for init when config exists (manual verification)

## Implementation - Command

- [x] [impl] [x] [reviewed] Implement init.scala command with full workflow
- [x] [impl] [x] [reviewed] Handle --force flag for config overwrite
- [x] [impl] [x] [reviewed] Add env var hint messages for Linear/YouTrack

## Verification

- [x] [verify] Run all tests and ensure they pass
- [x] [verify] Manual test: ./iw init in clean git repo
- [x] [verify] Manual test: ./iw init outside git repo (should error)
- [x] [verify] Manual test: ./iw init when config exists (should error, suggest --force)

## Notes

- No API calls in this phase - token validation is Phase 3
- Config file should be committable (no secrets)
- Use HOCON format with Typesafe Config library
