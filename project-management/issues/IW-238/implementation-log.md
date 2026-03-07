# Implementation Log: Add deterministic phase lifecycle commands

Issue: IW-238

This log tracks the evolution of implementation across phases.

---

## Phase 1: Domain Layer (2026-03-07)

**Layer:** Domain

**What was built:**
- `.iw/core/model/PhaseBranch.scala` - PhaseNumber opaque type (validates 1-99, zero-pads) and PhaseBranch case class for sub-branch name derivation
- `.iw/core/model/CommitMessage.scala` - Pure commit message construction with title and optional bulleted items
- `.iw/core/model/PhaseTaskFile.scala` - Markdown parsing/rewriting: markComplete (Phase Status line) and markReviewed (checkbox updates)
- `.iw/core/model/PhaseOutput.scala` - StartOutput, CommitOutput, PrOutput case classes with shared toJson helper

**Dependencies on other layers:**
- None (pure domain layer, no I/O)

**Testing:**
- Unit tests: 39 tests added (13 PhaseBranch, 6 CommitMessage, 13 PhaseTaskFile, 7 PhaseOutput)
- Integration tests: 0 (not applicable for pure domain)

**Code review:**
- Iterations: 2
- Review file: review-phase-01-20260307-224247.md
- Fixed: test package mismatch (iw.core.domain -> iw.tests), idempotency test assertion, unused import, extracted shared toJson helper, single-pass markComplete

**Notable decisions:**
- `Integer.parseInt(pn)` kept in PhaseNumber.toInt instead of `pn.toInt` — Scala 3 compiler detects the latter as infinite recursion within opaque type extension
- PhaseOutput uses raw String fields (not PhaseNumber/IssueId types) for simplicity as output DTOs
- toJson embedded in model case classes as pragmatic trade-off (mild FCIS deviation acknowledged)

**Files changed:**
```
A	.iw/core/model/CommitMessage.scala
A	.iw/core/model/PhaseBranch.scala
A	.iw/core/model/PhaseOutput.scala
A	.iw/core/model/PhaseTaskFile.scala
A	.iw/core/test/CommitMessageTest.scala
A	.iw/core/test/PhaseBranchTest.scala
A	.iw/core/test/PhaseOutputTest.scala
A	.iw/core/test/PhaseTaskFileTest.scala
```

---
