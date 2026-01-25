# Phase 4 Context: Title-only creation

**Issue:** IW-103
**Phase:** 4 - Title-only creation
**Story:** Story 6 - Create issue with title only (minimal usage)

## User Story

```gherkin
Feature: Create issue with title only
  As a developer in a hurry
  I want to create an issue with just a title
  So that I can quickly log something without writing full description

Scenario: Create GitHub issue with title only
  Given I am in a project configured for GitHub tracker
  And gh CLI is installed and authenticated
  When I run "./iw issue create --title 'Quick bug note'"
  Then a new issue is created in GitHub repository
  And the issue has title "Quick bug note"
  And the issue has empty description
  And I see output "Issue created: #125"
```

## Status: ALREADY IMPLEMENTED

This phase was **already implemented in Phase 2**. The `IssueCreateParser` was designed with optional `--description` from the start:

```scala
// From IssueCreateParser.scala
case class IssueCreateRequest(
  title: String,
  description: Option[String]  // ← Optional from day 1
)

def parse(args: Seq[String]): Either[String, IssueCreateRequest] =
  val title = extractFlagValue(args, "--title")
  if title.isEmpty then
    return Left("--title flag is required")
  val description = extractFlagValue(args, "--description")  // ← Optional
  Right(IssueCreateRequest(title.get, description))
```

## Verification

The E2E test `"issue create with title only succeeds"` already exists and passes (test #8 in issue-create.bats).

## Acceptance Criteria

- [x] Command works with only --title flag (no --description required)
- [x] Empty description is passed to tracker API
- [x] No client-side validation requires description

## Phase Status: Complete (No additional work needed)
