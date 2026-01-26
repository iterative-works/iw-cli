# Phase 3 Tasks: Integrate llms.txt with iw-command-creation skill

**Issue:** IW-126
**Phase:** 3 of 3
**Context:** `phase-03-context.md`

## Setup

- [x] [prep] Read current skill file and understand structure
- [x] [prep] Verify llms.txt and docs/ paths exist

## Implementation

- [x] [impl] Update "Finding Core Module Documentation" section to reference llms.txt
- [x] [impl] Add note about llms.txt format and how to use it
- [x] [impl] Update "Available Core Modules" section to mention detailed docs location

## Verification

- [x] [verify] Test path resolution: `$IW_CORE_DIR/../llms.txt` exists
- [x] [verify] Verify skill YAML frontmatter is still valid
- [x] [verify] Verify markdown structure is correct

## Acceptance Criteria Checklist

- [x] Skill references llms.txt instead of source files
- [x] Path uses relative reference from $IW_CORE_DIR
- [x] Skill still provides quick module overview
- [x] Agents can follow the path to find documentation
