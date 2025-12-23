# Refactoring R1: Fix FCIS Architecture Violations

**Phase:** 6
**Created:** 2025-12-23
**Status:** Planned

## Decision Summary

Code review identified FCIS (Functional Core / Imperative Shell) architecture violations:

1. **Core depending on Infrastructure**: `GitHubHookDoctor` is in package `iw.core` but imports from `iw.core.infrastructure.CommandRunner`. This violates the principle that core should not depend on infrastructure.

2. **Scala 2 Pattern in Scala 3 Code**: `GhPrerequisiteError` in `GitHubClient.scala` uses the verbose Scala 2 sealed trait + case object pattern when Scala 3's enum feature would be more concise and idiomatic.

## Current State

### GitHubHookDoctor.scala

```scala
// Current location: .iw/core/GitHubHookDoctor.scala
package iw.core  // In core package

import iw.core.infrastructure.CommandRunner  // BUT imports from infrastructure!

object GitHubHookDoctor:
  def checkGhInstalled(config: ProjectConfiguration): CheckResult = ...
  def checkGhAuthenticated(config: ProjectConfiguration): CheckResult = ...
```

### GitHubClient.scala (GhPrerequisiteError)

```scala
// Current: Scala 2 pattern (verbose)
sealed trait GhPrerequisiteError
case object GhNotInstalled extends GhPrerequisiteError
case object GhNotAuthenticated extends GhPrerequisiteError
case class GhOtherError(message: String) extends GhPrerequisiteError
```

## Target State

### GitHubHookDoctor.scala

```scala
// New location: .iw/core/infrastructure/GitHubHookDoctor.scala
package iw.core.infrastructure  // Now in infrastructure package

// No cross-layer import needed - CommandRunner is in same package

object GitHubHookDoctor:
  def checkGhInstalled(config: ProjectConfiguration): CheckResult = ...
  def checkGhAuthenticated(config: ProjectConfiguration): CheckResult = ...
```

### GitHubClient.scala (GhPrerequisiteError)

```scala
// Target: Scala 3 enum (concise)
enum GhPrerequisiteError:
  case GhNotInstalled
  case GhNotAuthenticated
  case GhOtherError(message: String)
```

## Constraints

- PRESERVE: All existing functionality must work identically
- PRESERVE: All unit tests must pass
- PRESERVE: All E2E tests must pass
- PRESERVE: Hook discovery mechanism in doctor.scala
- DO NOT TOUCH: Hook command file (.iw/commands/github.hook-doctor.scala) - it already imports from iw.core correctly

## Tasks

- [ ] [impl] [Analysis] Verify infrastructure directory exists and understand package structure
- [ ] [impl] [Refactor] Move GitHubHookDoctor.scala to .iw/core/infrastructure/
- [ ] [impl] [Refactor] Update package declaration in GitHubHookDoctor.scala to iw.core.infrastructure
- [ ] [impl] [Refactor] Update import in github.hook-doctor.scala command to use new package
- [ ] [impl] [Refactor] Convert GhPrerequisiteError to Scala 3 enum in GitHubClient.scala
- [ ] [impl] [Refactor] Update pattern matches to use enum syntax if needed
- [ ] [impl] [Test] Update GitHubHookDoctorTest.scala imports
- [ ] [impl] [Verify] Run unit tests - all must pass
- [ ] [impl] [Verify] Run E2E tests - all must pass

## Verification

- [ ] All existing unit tests pass
- [ ] All existing E2E tests pass
- [ ] GitHubHookDoctor is in iw.core.infrastructure package
- [ ] GhPrerequisiteError uses Scala 3 enum syntax
- [ ] No cross-layer dependencies from core to infrastructure
