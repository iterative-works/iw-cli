# Code Review Results

**Review Context:** Phase 3: Server lifecycle management for IWLE-100
**Files Reviewed:** 16 files
**Skills Applied:** 4 (architecture, scala3, testing, composition)
**Timestamp:** 2025-12-20
**Git Context:** git diff c4ea937...HEAD

---

<review skill="architecture">

## Architecture Review

### Critical Issues

None found.

### Warnings

1. **ServerClient Lazy Start Coupling**
   - **Location:** `.iw/core/ServerClient.scala:42-58`
   - **Problem:** HTTP client has logic to start the server if not running, mixing client and server lifecycle concerns
   - **Impact:** Tight coupling - client should not know how to start the server
   - **Recommendation:** Consider moving server start logic to dedicated service or command layer

2. **Test Package Organization Inconsistency**
   - **Location:** `.iw/core/test/` directory
   - **Problem:** Test files use mixed package declarations - some use `iw.core.test`, others use production packages like `iw.core.infrastructure`
   - **Impact:** Makes it harder to find corresponding tests
   - **Recommendation:** Tests should mirror production package structure consistently

### Suggestions

1. **Command Scripts Path Duplication**
   - **Location:** `.iw/commands/server.scala:19-30`, `.iw/commands/dashboard.scala:21-23`
   - **Problem:** Path construction logic duplicated across command scripts
   - **Recommendation:** Extract to shared `ServerPaths` utility object

2. **ServerStatus Could Use Enum for State**
   - **Location:** `.iw/core/ServerStatus.scala:9-15`
   - **Problem:** Using boolean `running` with optional fields
   - **Recommendation:** Consider `enum ServerStatus { case Stopped; case Running(...) }`

3. **Consider Repository Traits for Testing**
   - **Location:** Repository objects are concrete implementations
   - **Recommendation:** Define traits as ports for future flexibility

</review>

---

<review skill="scala3">

## Scala 3 Review

### Critical Issues

None found.

### Warnings

None found.

### Suggestions

1. **Consider Enum for Server Commands**
   - **Location:** `.iw/commands/server.scala:186-193`
   - **Problem:** String pattern matching for commands
   - **Recommendation:** Define `enum ServerCommand { case Start, Stop, Status }` for type safety

2. **Functional Health Check Retry**
   - **Location:** `.iw/commands/server.scala:79-89`
   - **Problem:** Imperative while loop for health check retries
   - **Recommendation:** Use `LazyList.continually(...).take(maxRetries).exists(identity)`

3. **Explicit Java Optional Conversion**
   - **Location:** `.iw/core/ProcessManager.scala:64`
   - **Problem:** Implicit Java Optional handling
   - **Recommendation:** Use `.toScala` with `scala.jdk.OptionConverters` for clarity

</review>

---

<review skill="testing">

## Testing Review

### Critical Issues

None found.

### Warnings

None found.

### Suggestions

1. **Consider Property-Based Testing for Port Validation**
   - **Location:** `.iw/core/test/ServerConfigTest.scala`
   - **Problem:** Manual enumeration of test cases
   - **Recommendation:** ScalaCheck would provide better coverage for range validation

2. **Extract Test Data Builders**
   - **Location:** `.iw/core/test/CaskServerTest.scala`
   - **Problem:** Request JSON construction duplicated across tests
   - **Recommendation:** Create `TestData.worktreeRequest()` builder

3. **Missing Edge Case: Negative Duration**
   - **Location:** `.iw/core/test/ServerLifecycleServiceTest.scala`
   - **Problem:** No test for `now` before `startedAt`
   - **Recommendation:** Add test for clock adjustment edge case

4. **Missing E2E Tests for Server Commands**
   - **Location:** No BATS tests for `iw server start/stop/status`
   - **Problem:** CLI integration not tested end-to-end
   - **Recommendation:** Add E2E tests in a follow-up phase

</review>

---

<review skill="composition">

## Composition Review

### Critical Issues

None found.

### Warnings

1. **Mutable State in ProcessManager.stopProcess**
   - **Location:** `.iw/core/ProcessManager.scala:78-81`
   - **Problem:** Uses `var waited` with while loop for process exit waiting
   - **Impact:** Not purely functional, harder to reason about
   - **Recommendation:** Use tail recursion or functional retry helper

### Suggestions

1. **Extract Error Handling Composition**
   - **Location:** `.iw/commands/server.scala` (multiple locations)
   - **Problem:** Repetitive `Either` matching with `System.exit(1)`
   - **Recommendation:** Create `extension [A](either: Either[String, A]).orDieWith(...)`

2. **CaskServer.start Could Return Server Instance**
   - **Location:** `.iw/core/CaskServer.scala:176-183`
   - **Problem:** Blocking Unit-returning function, can't access instance for shutdown
   - **Recommendation:** Return `io.undertow.Undertow` for composability

3. **Consider Type Classes for Path Providers**
   - **Location:** Throughout codebase
   - **Problem:** Path configuration scattered, hard to test
   - **Recommendation:** Use `PathProvider[Env]` type class for environment abstraction

</review>

---

## Summary

- **Critical issues:** 0 (no blockers for merge)
- **Warnings:** 3 (should consider fixing)
- **Suggestions:** 12 (nice to have improvements)

### By Skill
- architecture: 0 critical, 2 warnings, 3 suggestions
- scala3: 0 critical, 0 warnings, 3 suggestions
- testing: 0 critical, 0 warnings, 4 suggestions
- composition: 0 critical, 1 warning, 3 suggestions

### Verdict

**PASS** - No critical issues found. The implementation follows FCIS patterns correctly with pure domain/application layers. Warnings are about coupling and code organization that can be addressed in future iterations. Suggestions focus on idiomatic Scala 3 patterns and improved testability.

The Phase 3 implementation is ready for merge after marking review checkboxes complete.
