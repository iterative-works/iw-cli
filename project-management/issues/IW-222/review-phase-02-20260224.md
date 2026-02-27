# Code Review Results

**Review Context:** Phase 2: Infrastructure Layer — adapter moves, StateReader, TmuxAdapter.sendKeys for issue IW-222 (Iteration 1/3)
**Files Reviewed:** 10 (+ 6 new untracked files)
**Skills Applied:** code-review-architecture, code-review-style, code-review-testing, code-review-scala3
**Timestamp:** 2026-02-24
**Git Context:** `git diff e7c8189`

---

<review skill="code-review-architecture">

## Critical Issues

None.

## Warnings

None.

## Suggestions

1. **Consider extracting server path constants** — `.iw/core/adapters/StateReader.scala:13` and `.iw/core/adapters/ServerClient.scala:13-16` both compute server directory paths inline. If this pattern appears in 3+ places, consider extracting to `Constants.Paths`.

2. **Consider consolidating DefaultStatePath** — `StateReader.DefaultStatePath` duplicates the path logic in `ServerClient`. Could share a single constant.

</review>

---

<review skill="code-review-style">

## Critical Issues

None.

## Warnings

1. **Package inconsistency in TmuxAdapterSendKeysTest** — `.iw/core/test/TmuxAdapterSendKeysTest.scala` uses `package iw.tests` while `StateReaderTest.scala` uses `package iw.core.test`. Should standardize.

2. **Magic number `-1` for default port in ServerClient** — `.iw/core/adapters/ServerClient.scala:39` uses `port: Int = -1` as sentinel. Pre-existing code (not introduced in Phase 2), but noted for future cleanup.

## Suggestions

1. **Import simplification** — `import iw.core.model.ServerStateCodec.{given, *}` could be just `import iw.core.model.ServerStateCodec.*` in Scala 3.

2. **Scaladoc enhancement** — `StateReader.read()` could clarify the distinction between "missing file → empty state" vs "malformed file → Left(error)".

</review>

---

<review skill="code-review-testing">

## Critical Issues

None (reclassified from reviewer's assessment — see notes below).

## Warnings

1. **StateReaderTest happy path only asserts map sizes** — `.iw/core/test/StateReaderTest.scala` first test creates exhaustive JSON but only checks `.size` on maps, not actual field values. Add assertions on at least one entry's data to ensure correct deserialization.

2. **TmuxAdapterSendKeysTest is an integration test** — Tests create real tmux sessions. Consistent with existing codebase pattern (ProcessManagerTest uses real processes), but worth noting these require `tmux` to be installed.

3. **Missing tests for pre-existing ProcessManager methods** — `stopProcess()` and `spawnServerProcess()` are untested. These were moved from dashboard/ (not new code), so out of scope for this phase, but noted for future work.

4. **Test package inconsistency** — `TmuxAdapterSendKeysTest` uses `iw.tests`, while `StateReaderTest` uses `iw.core.test`, and other tests use `iw.core.infrastructure`. Pre-existing inconsistency.

## Suggestions

1. Add deeper assertions in `StateReaderTest` to verify at least one worktree entry's field values (path, trackerType, etc.) after deserialization.

2. Consider testing `StateReader.read()` without arguments to verify default path behavior.

</review>

---

<review skill="code-review-scala3">

## Critical Issues

None.

## Warnings

1. **Redundant import pattern** — `.iw/core/adapters/StateReader.scala:7`: `import ServerStateCodec.{given, *}` — the `*` wildcard includes givens in Scala 3. Simplify to `import ServerStateCodec.*`.

## Suggestions

1. **Prefer `os.exists` over `Files.exists`** — `StateReader.scala:24` mixes `java.nio.file` with `os-lib`. The rest of the codebase uses `os-lib` consistently.

2. **Extension method for ProcessResult → Either** — Repeated pattern of `if result.exitCode == 0 then Right(()) else Left(...)` across adapter methods could be extracted. Low priority, current style is clear.

3. Good use of `@tailrec`, Scala 3 `if-then-else`, and pattern matching on `Try` results throughout.

</review>

---

## Summary

| Severity | Count |
|----------|-------|
| Critical | 0 |
| Warnings | 5 (1 actionable for Phase 2, 4 pre-existing/noted) |
| Suggestions | 8 |

**Actionable for Phase 2:**
- Fix TmuxAdapterSendKeysTest package from `iw.tests` to match codebase convention
- Add deeper assertions in StateReaderTest (verify field values, not just map sizes)
- Simplify `ServerStateCodec.{given, *}` import to `ServerStateCodec.*`

**Pre-existing (out of scope):**
- Magic number port sentinel in ServerClient
- Missing stopProcess/spawnServerProcess tests in ProcessManager
- Test package naming inconsistency across codebase

**Verdict: PASS with suggestions** — No critical issues. The 3 actionable items are minor improvements.
