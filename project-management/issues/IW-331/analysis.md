# Technical Analysis: Add action hook points to start, doctor, and phase-merge

**Issue:** IW-331
**Created:** 2026-04-03
**Status:** Draft

## Problem Statement

Three commands (`start`/`open`, `doctor --fix`, `phase-merge`) hardcode Claude-specific behavior directly in their command scripts. This couples iw-cli's core to a specific AI tool, preventing the bounded context extraction planned in dev-docs#123 and blocking IW-324 (removal of AI-specific code from core).

The existing hook mechanism only supports `Check` value discovery for `doctor`. Commands need a parallel mechanism for **action hooks** — plugin-provided behavior invoked at specific extension points — so that Claude-specific logic can move into a plugin without losing functionality.

## Proposed Solution

### High-Level Approach

Introduce three new trait types in `core/model/` — `SessionAction`, `FixAction`, and `RecoveryAction` — alongside context case classes that carry the data each hook needs. These follow the same pattern as the existing `Check` type: pure trait definitions in model, discovered via reflection in command scripts.

Each affected command replaces its hardcoded Claude invocation with a reflection-based lookup of the appropriate action type from `IW_HOOK_CLASSES`. When no hook provides an action, the command warns and degrades gracefully (no session command sent, no fix attempted, no recovery attempted).

No changes to `iw-run` are needed. The existing hook file naming convention (`*.hook-{command}.scala`) and class discovery mechanism already handle discovery for arbitrary commands. We only need new hook files named `*.hook-start.scala`, `*.hook-doctor.scala` (already exists for checks), and `*.hook-phase-merge.scala`.

### Why This Approach

The existing `Check` discovery pattern is proven and well-understood. Extending it to action types is the minimal change that enables plugin-provided behavior. The alternative — a registration-based plugin API — would be over-engineered for what amounts to "find objects with fields of type X via reflection." The current reflection approach, while not elegant, is consistent with what already works and requires zero infrastructure changes.

## Architecture Design

**Purpose:** Define WHAT components each layer needs, not HOW they're implemented.

### Domain Layer

**Components:**
- `SessionAction` trait + `SessionContext` case class (for start/open hooks)
- `FixAction` trait + `DoctorFixContext` case class (for doctor --fix hooks)
- `RecoveryAction` trait + `RecoveryContext` case class (for phase-merge hooks)

**Responsibilities:**
- Define the contract each hook type must satisfy
- Carry context data to hooks without coupling to I/O
- `SessionAction.run` returns `Option[String]` (tmux command to send, or None)
- `FixAction.fix` returns `Int` (exit code)
- `RecoveryAction.recover` returns `Unit` (side-effecting by nature — the hook runs an external tool)

**Estimated Effort:** 0.5-1 hours
**Complexity:** Straightforward

---

### Application Layer

**Components:**
- Generic hook collection function (generalize `collectHookChecks` to work with any type)
- Hook invocation logic in each command script (start, open, doctor, phase-merge)

**Responsibilities:**
- Discover hook objects via `IW_HOOK_CLASSES` reflection
- Extract values of the appropriate action type from hook objects
- Invoke actions with the right context
- Handle missing hooks gracefully (warn, not error)
- Handle multiple hooks (first non-None wins for SessionAction; single FixAction/RecoveryAction expected)

**Estimated Effort:** 1.5-2 hours
**Complexity:** Moderate

---

### Infrastructure Layer

**Components:**
- No new infrastructure components needed

**Responsibilities:**
- No new persistence or external integration concerns
- Existing `ProcessAdapter`, `TmuxAdapter` continue to be used by hook implementations (not by the hook mechanism itself)

**Estimated Effort:** 0 hours
**Complexity:** N/A

---

### Presentation Layer

**Components:**
- Warning messages when no hook is found for an action
- Adjusted output messages (remove Claude-specific wording from start/open/doctor/phase-merge)

**Responsibilities:**
- User feedback when hooks are missing
- Consistent messaging across all three hook points

**Estimated Effort:** 0.5 hours
**Complexity:** Straightforward

---

## Technical Decisions

### Patterns

- Same reflection-based discovery as existing `Check` hooks — no new discovery mechanism
- Traits with context case classes (Strategy pattern, effectively)
- Graceful degradation: missing hooks produce warnings, not errors

### Technology Choices

- **Frameworks/Libraries**: No new dependencies; uses existing Java reflection
- **Data Storage**: None
- **External Systems**: None (hooks call external tools, but that's the hook's concern)

### Integration Points

- `core/model/` traits are consumed by command scripts via reflection
- `iw-run` already discovers `*.hook-{command}.scala` files — no changes needed
- Hook files (to be created in IW-324/dev-docs#123) will import from `core/model/` and `core/adapters/`

## Technical Risks & Uncertainties

### CLARIFY: Generic hook collection vs. per-command duplication

The existing `collectHookChecks()` in `doctor.scala` is specific to the `Check` type. Three more commands will need similar reflection logic. Should we extract a generic `collectHookValues[T]` function into `core/`, or duplicate the pattern in each command?

**Questions to answer:**
1. Is a generic function worth the complexity of parameterized reflection?
2. Should it live in `core/model/` (pure) or `core/adapters/` (reflection = side effect)?

**Options:**
- **Option A**: Extract generic function into `core/adapters/HookDiscovery.scala`. Pros: DRY, single place to maintain. Cons: Reflection with type parameters in Scala 3 requires `ClassTag`, slightly more complex.
- **Option B**: Duplicate the pattern (3 small functions, ~15 lines each). Pros: Simple, each command is self-contained. Cons: Four copies of nearly identical reflection code.
- **Option C**: Extract a generic function but keep it in a shared file compiled with all commands (not in core/). Pros: DRY without putting reflection in core. Cons: Need a mechanism to share code across command scripts.

**Impact:** Code maintainability. All options work functionally.

---

### CLARIFY: SessionAction conflict resolution

When multiple hooks provide a `SessionAction`, what happens?

**Questions to answer:**
1. Should first non-None win, or should we error on conflict?
2. Is there a realistic scenario where multiple plugins provide session actions?

**Options:**
- **Option A**: First non-None wins (ordered by class name or discovery order). Simple, but potentially confusing.
- **Option B**: Error if multiple non-None results. Strict, prevents ambiguity.
- **Option C**: Allow chaining (all hooks run in sequence). More flexible but complex.

**Impact:** Determines behavior when users install multiple plugins with session hooks.

---

### CLARIFY: FixAction and RecoveryAction return types

The issue proposes `FixAction.fix` returns `Int` (exit code) and `RecoveryAction.recover` returns `Unit`. Both are inherently side-effecting (they run external tools). Should the traits acknowledge this more explicitly?

**Questions to answer:**
1. Is `Unit` the right return for `RecoveryAction.recover`? It hides whether recovery succeeded.
2. Should we use `Either[String, Unit]` or similar to signal success/failure?

**Options:**
- **Option A**: Keep as proposed (`Int` and `Unit`). Simple, matches current behavior.
- **Option B**: Use `Either[String, Unit]` for both. More functional, enables better error reporting.

**Impact:** Error reporting quality in phase-merge recovery loop.

## Total Estimates

**Per-Layer Breakdown:**
- Domain Layer: 0.5-1 hours
- Application Layer: 1.5-2 hours
- Infrastructure Layer: 0 hours
- Presentation Layer: 0.5 hours

**Total Range:** 2.5 - 3.5 hours

**Confidence:** High

**Reasoning:**
- All three hook points follow the same well-established pattern (Check discovery)
- No new infrastructure or tooling needed
- The existing `iw-run` discovery mechanism handles hook files without changes
- The hardcoded Claude code in each command clearly shows what needs to be extracted

## Testing Strategy

### Per-Layer Testing

**Domain Layer:**
- Unit: Verify context case classes construct correctly (trivial, may not need dedicated tests)
- Unit: Verify trait contracts compile with test implementations

**Application Layer:**
- Unit: Test generic hook collection with mock classloader (if Option A from CLARIFY)
- Integration: Test that commands behave correctly when no hooks are present (warn + graceful degradation)
- Integration: Test that commands invoke hooks when present (requires a test hook file)

**Presentation Layer:**
- E2E: Test `start --prompt` with no hook installed (should warn)
- E2E: Test `doctor --fix` with no hook installed (should warn)
- E2E: Existing doctor hook tests still pass (regression)

**Test Data Strategy:**
- Create minimal test hook files for E2E tests
- Use existing BATS test infrastructure

**Regression Coverage:**
- Existing `*.hook-doctor.scala` check discovery must continue working unchanged
- `--prompt` flag parsing unchanged
- `--fix` flag parsing unchanged
- `--max-retries` flag parsing unchanged

## Deployment Considerations

### Database Changes
None.

### Configuration Changes
None. Hook discovery uses existing `IW_HOOK_CLASSES` mechanism.

### Rollout Strategy
This is a pure refactoring of extension points. Until a plugin provides hook implementations (dev-docs#123 Phase 2), the commands will warn that no hook is found and skip the AI-specific behavior. This is a deliberate intermediate state.

### Rollback Plan
Revert the branch. No data or configuration changes to worry about.

## Dependencies

### Prerequisites
- IW-323 (Plugin command directory support) — already merged

### Layer Dependencies
- Domain layer must be implemented first (traits needed by command scripts)
- Application layer depends on domain layer
- Presentation layer changes are interleaved with application layer

### External Blockers
- None. This issue enables IW-324 and dev-docs#123 Phase 2, but does not depend on them.

## Risks & Mitigations

### Risk 1: Reflection fragility with Scala 3 objects
**Likelihood:** Low
**Impact:** Medium
**Mitigation:** The existing `collectHookChecks()` already works with Scala 3 object reflection. New action types follow the identical pattern (find fields by return type on `MODULE$` instance).

### Risk 2: Intermediate state where Claude behavior disappears
**Likelihood:** Medium
**Impact:** Low
**Mitigation:** During the transition (after this PR, before the kanon plugin), `start --prompt`, `doctor --fix`, and `phase-merge` recovery will warn and skip AI behavior. This is expected and documented. Users who need the Claude behavior should not update until the plugin is available, or this PR can retain the hardcoded behavior behind a fallback until the plugin exists.

---

## Implementation Sequence

**Recommended Layer Order:**

1. **Domain Layer** - Define traits and context types in `core/model/`. Pure types, no dependencies, foundation for command changes.
2. **Application Layer** - Modify command scripts to use reflection-based hook discovery and invocation. This is the bulk of the work.
3. **Presentation Layer** - Adjust warning/info messages in commands. Interleaved with application layer changes.

**Ordering Rationale:**
- Domain types must exist before commands can reference them
- Each command can be modified independently (start/open, doctor, phase-merge) — parallelizable
- No infrastructure layer needed, simplifying the sequence
- Testing can proceed per-command as each is updated

## Documentation Requirements

- [ ] Code documentation (inline comments for hook trait contracts)
- [ ] Architecture decision record (hook mechanism extension pattern)
- [ ] Update CLAUDE.md if hook patterns section needs expansion

---

**Analysis Status:** Ready for Review

**Next Steps:**
1. Resolve CLARIFY markers with stakeholders
2. Run **wf-create-tasks** with the issue ID
3. Run **wf-implement** for layer-by-layer implementation
