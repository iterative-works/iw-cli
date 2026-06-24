# Phase 1: Domain, config & serialization

**Issue:** IW-389 — Support Forgejo issue tracker
**Layer:** 1 — Domain & Configuration (no dependencies; base layer)
**Estimate:** 2–4h

## Goals

Thread a new `Forgejo` variant through the pure domain/config layer so that a
project can be described as a Forgejo project in `.iw/config.conf` and that
description round-trips losslessly through the model.

Concretely, after this phase:

- `IssueTrackerType.Forgejo` exists as an enum case.
- `tracker.type = forgejo` parses to `IssueTrackerType.Forgejo` and serializes
  back to the same string.
- Forgejo's config field set (`repository`, `baseUrl`, optional `teamPrefix`)
  round-trips through `ConfigSerializer`.
- The enum change forces the compiler to surface every downstream `match
  IssueTrackerType` site that must eventually handle Forgejo — a desirable,
  explicit failure mode for later phases.

This is the foundation everything else (adapter, wiring, command dispatch,
init/doctor) builds on. It is entirely pure (HOCON ↔ model); no I/O.

## Scope

### In scope

- `core/model/Constants.scala`: add `TrackerTypeValues.Forgejo = "forgejo"`.
- `core/model/Config.scala`:
  - Add `Forgejo` to `enum IssueTrackerType` (currently line 103).
  - Handle the `Forgejo` case in `ConfigSerializer.toHocon` (the `trackerType
    match`, lines 205–209).
  - Handle the `forgejo` string in `ConfigSerializer.fromHocon` (the
    `trackerTypeStr match`, lines 240–249).
- Config round-trip unit tests for Forgejo (mirroring the existing GitHub /
  YouTrack serializer tests in `core/test/ConfigTest.scala`).

### Out of scope (later phases)

- The `ForgejoClient` HTTP adapter and any network/JSON work — **Phase 2**.
- `TrackerOps` capability wiring (`CommandEnv` / `LiveCommandEnv` /
  `FakeCommandEnv`) and command dispatch in `Issue.scala` — **Phase 3**.
- `Init` / `Doctor` integration and BATS smoke — **Phase 4**.
- PR creation / CI-check polling / `ForgeType` — **Phase 5**.
- The `FORGEJO_API_TOKEN` env-var constant and any auth resolution: the token is
  resolved in the command-dispatch layer (Phase 3), not here. Do **not** add it
  in this phase.

## Dependencies

- **Prior layers needed:** none. This is Layer 1, the base of the dependency
  graph. It imports only `com.typesafe.config.*` (already used) and
  `iw.core.model.Constants`.
- **What depends on this layer:** everything downstream. Phase 2's adapter keys
  off `IssueTrackerType.Forgejo`; Phase 3's `TrackerOps`/`Issue.scala` dispatch
  matches on it; Phase 4's `Init`/`Doctor` reference both the enum and the
  `forgejo` config string.

## Approach

Mirror the existing trackers exactly. The relevant patterns, verified against
the real source:

### 1. Enum case — `core/model/Config.scala:102–103`

```scala
enum IssueTrackerType:
  case Linear, YouTrack, GitHub, GitLab
```

Add `Forgejo`:

```scala
enum IssueTrackerType:
  case Linear, YouTrack, GitHub, GitLab, Forgejo
```

This is an exhaustive discriminator. Adding the case will (intentionally) break
compilation at the two `match` sites in `ConfigSerializer` below, plus any other
exhaustive matches across the codebase. In this phase we only fix the two
serializer sites; the remaining compiler errors are the work-list that Phases
3–5 consume. (If other `model/` matches break, fix them here only if they live
in Layer 1; defer adapter/command matches to their phases.)

### 2. Constant — `core/model/Constants.scala:49–53`

```scala
object TrackerTypeValues:
  val Linear = "linear"
  val YouTrack = "youtrack"
  val GitHub = "github"
  val GitLab = "gitlab"
```

Add `val Forgejo = "forgejo"`.

### 3. `toHocon` — `core/model/Config.scala:205–209`

```scala
val trackerTypeStr = config.trackerType match
  case IssueTrackerType.Linear   => Constants.TrackerTypeValues.Linear
  case IssueTrackerType.YouTrack => Constants.TrackerTypeValues.YouTrack
  case IssueTrackerType.GitHub   => Constants.TrackerTypeValues.GitHub
  case IssueTrackerType.GitLab   => Constants.TrackerTypeValues.GitLab
```

Add the `Forgejo` arm → `Constants.TrackerTypeValues.Forgejo`.

The field emission below (`trackerFields`, lines 213–218) is **already generic**:
it emits `repository`, `team` (only if non-empty), `teamPrefix`, and `baseUrl`
from whatever the model holds. Forgejo's field set (`repository` + `baseUrl` +
optional `teamPrefix`, `team` unused) flows through this unchanged — **no edit
needed there**.

### 4. `fromHocon` — `core/model/Config.scala:240–249`

```scala
val trackerTypeEither = trackerTypeStr match
  case Constants.TrackerTypeValues.Linear   => Right(IssueTrackerType.Linear)
  case Constants.TrackerTypeValues.YouTrack => Right(IssueTrackerType.YouTrack)
  case Constants.TrackerTypeValues.GitHub   => Right(IssueTrackerType.GitHub)
  case Constants.TrackerTypeValues.GitLab   => Right(IssueTrackerType.GitLab)
  case other => Left(s"Unknown tracker type: $other")
```

Add a `Constants.TrackerTypeValues.Forgejo => Right(IssueTrackerType.Forgejo)`
arm before the `other` fallthrough.

The field-parsing block that follows (lines 251–316) is **already generic** and
shared across all trackers:
- `repository` validation (owner/repo format + safe-segment check),
  lines 258–272.
- `baseUrl` validation (must start with `http://` / `https://`),
  lines 288–294.
- `teamPrefix` validation via `TeamPrefixValidator`, lines 274–279.

Forgejo reuses all three predicates as-is. **No new parsing branch is needed** —
just the enum-string mapping.

### On "Forgejo requires repository and baseUrl"

**Verified caveat — flag during implementation review.** The analysis (line 89)
says Forgejo should *require* `repository` and `baseUrl`. But the current
`fromHocon` reads **every** field optionally and enforces **no** per-tracker
required-field rule: e.g. a GitHub config with no `repository` succeeds today
(`core/test/ConfigTest.scala:168–182`, "ConfigSerializer succeeds when GitHub
config missing repository"). There is therefore **no existing required-field
predicate to reuse** at the serializer level — the existing predicates only
validate *format when present*, not *presence*.

Two consistent options; pick during implementation (lean toward A to stay
minimal and match how GitHub/GitLab already behave):

- **A (match existing behaviour, recommended):** keep parsing optional. Required-
  field enforcement happens later, at use-time in the command/adapter layer
  (Phase 2/3), exactly as GitHub/GitLab do today. This keeps Phase 1 a pure,
  additive serializer change with no behavioural divergence from the other
  trackers.
- **B (enforce presence here):** add a Forgejo-specific guard in `fromHocon`
  that returns `Left` when `repository` or `baseUrl` is absent. This is *new*
  validation behaviour, not a reuse of existing predicates, and would make
  Forgejo the only tracker with presence enforcement at parse time — an
  inconsistency worth a deliberate decision.

If B is chosen, the format predicates (owner/repo, http(s) prefix) still apply
on top of the presence check. Do not silently introduce B; raise it.

## Files to Modify

| File | Change |
|------|--------|
| `core/model/Config.scala` | Add `Forgejo` to `enum IssueTrackerType` (line 103). Add `Forgejo` arm to `ConfigSerializer.toHocon`'s `trackerType match` (lines 205–209). Add `forgejo` arm to `ConfigSerializer.fromHocon`'s `trackerTypeStr match` (lines 240–249). |
| `core/model/Constants.scala` | Add `val Forgejo = "forgejo"` to `object TrackerTypeValues` (lines 49–53). |
| `core/test/ConfigTest.scala` | Add Forgejo serializer round-trip + parse tests (see Testing Strategy). |
| `core/test/ConstantsTest.scala` | Add an assertion for `Constants.TrackerTypeValues.Forgejo == "forgejo"`, mirroring lines 50–54. |

No other files should change in this phase. The `ProjectConfigurationJson`
upickle codec (lines 320–331) derives `IssueTrackerType` via
`IssueTrackerType.valueOf` and needs no edit — adding the enum case is
sufficient.

## Testing Strategy

TDD: write the failing test first, watch it fail (for the enum/string mapping
the failure is a compile error or `Unknown tracker type: forgejo`), then make it
pass.

Mirror the existing GitHub/YouTrack tests in `core/test/ConfigTest.scala`:
- Round-trip template: "ConfigSerializer round-trip for GitHub config"
  (lines 151–166).
- `toHocon` assertion template: "ConfigSerializer serializes GitHub config to
  HOCON" (lines 121–131).
- `fromHocon` assertion template: "ConfigSerializer deserializes HOCON with
  GitHub tracker" (lines 133–149).
- `baseUrl` scheme rejection template: "ConfigSerializer rejects trackerBaseUrl
  with non-http(s) scheme" (lines 224–241) — confirm it still fires for a
  `type = forgejo` config (proves the shared `baseUrl` predicate covers
  Forgejo).

Add at least these cases:

1. **`toHocon` emits `type = forgejo`** plus `repository` and `baseUrl` for a
   Forgejo config.
2. **`fromHocon` parses `type = forgejo`** with `repository` + `baseUrl`
   (+ optional `teamPrefix`) into `IssueTrackerType.Forgejo` with the expected
   fields.
3. **Round-trip**: `toHocon` → `fromHocon` preserves `trackerType`,
   `repository`, `baseUrl`, `teamPrefix`, `projectName`.
4. **Shared `repository` format validation** rejects a malformed Forgejo
   `repository` (e.g. `"invalid"` → "repository must be in owner/repo format"),
   proving reuse of the existing predicate.
5. **Shared `baseUrl` scheme validation** rejects a non-http(s) `baseUrl` under
   `type = forgejo`.
6. If **option B** (presence enforcement) is chosen: a Forgejo config missing
   `repository` or `baseUrl` returns `Left`. If **option A**, instead assert the
   current optional behaviour (missing fields → `None`, parse succeeds), matching
   the GitHub precedent at lines 168–182.

`ProjectConfiguration.create` (lines 153–172) is the convenient flat-parameter
constructor used by these tests (it sets `trackerType`, `repository`,
`teamPrefix`, `trackerBaseUrl`, `projectName`).

Run: `./iw ./test unit` (core.test via Mill munit), or `./mill core.test`.

## Acceptance Criteria

- [ ] `IssueTrackerType.Forgejo` enum case exists; the project compiles with no
      non-exhaustive-match warnings in `model/` (compile with `-Werror` per
      project convention).
- [ ] `Constants.TrackerTypeValues.Forgejo == "forgejo"`.
- [ ] `ConfigSerializer.toHocon` emits `type = forgejo` for a Forgejo config.
- [ ] `ConfigSerializer.fromHocon` parses `tracker.type = forgejo` to
      `IssueTrackerType.Forgejo`.
- [ ] A Forgejo config (`repository`, `baseUrl`, optional `teamPrefix`)
      round-trips losslessly through `toHocon` → `fromHocon`.
- [ ] Shared `repository` and `baseUrl` format predicates apply to Forgejo
      (malformed values rejected with the existing messages).
- [ ] Required-vs-optional `repository`/`baseUrl` behaviour is decided
      explicitly (option A or B above) and covered by a test.
- [ ] New Forgejo tests in `ConfigTest.scala` (+ the `ConstantsTest.scala`
      assertion) are green; the full `core.test` suite is green.
