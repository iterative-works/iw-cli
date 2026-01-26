# Phase 3 Tasks: Integrate llms.txt with iw-command-creation skill

**Issue:** IW-126
**Phase:** 3 of 3
**Context:** `phase-03-context.md`

## Setup

- [ ] [prep] Read current skill file and understand structure
- [ ] [prep] Verify llms.txt and docs/ paths exist

## Implementation

- [ ] [impl] Update "Finding Core Module Documentation" section to reference llms.txt
- [ ] [impl] Add note about llms.txt format and how to use it
- [ ] [impl] Update "Available Core Modules" section to mention detailed docs location

## Verification

- [ ] [verify] Test path resolution: `$IW_CORE_DIR/../llms.txt` exists
- [ ] [verify] Verify skill YAML frontmatter is still valid
- [ ] [verify] Verify markdown structure is correct

## Acceptance Criteria Checklist

- [ ] Skill references llms.txt instead of source files
- [ ] Path uses relative reference from $IW_CORE_DIR
- [ ] Skill still provides quick module overview
- [ ] Agents can follow the path to find documentation
