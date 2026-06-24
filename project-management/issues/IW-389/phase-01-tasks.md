# Phase 1 Tasks: Domain, config & serialization

**Issue:** IW-389 — Support Forgejo issue tracker
**Phase:** 1 — Domain, config & serialization (Layer 1: Domain & Configuration)

Pure domain/config change (HOCON ↔ model), no I/O. Thread a new `Forgejo`
variant through `IssueTrackerType`, the `forgejo` config string, and
`ConfigSerializer`. TDD: write the failing tests first, then make them pass.

## Setup

- [x] [setup] Confirm the anchors named in `phase-01-context.md` still hold: `enum IssueTrackerType` at `core/model/Config.scala:102–103`, `object TrackerTypeValues` at `core/model/Constants.scala:49–53`, `ConfigSerializer.toHocon`'s `trackerType match` at `core/model/Config.scala:205–209`, and `fromHocon`'s `trackerTypeStr match` at `core/model/Config.scala:240–249`.
- [x] [setup] Decide the required-vs-optional `repository`/`baseUrl` question. **Default: option A** — keep parsing optional, matching GitHub/GitLab (no parse-time presence enforcement; required-field checks deferred to Phase 2/3). Only choose option B (presence guard in `fromHocon`) with an explicit decision, since it would make Forgejo the only tracker enforcing presence at parse time. Record the decision; the test tasks below assume A unless overridden.

## Tests (write first — TDD)

- [x] [test] In `core/test/ConstantsTest.scala`, add an assertion that `Constants.TrackerTypeValues.Forgejo == "forgejo"`, mirroring the existing tracker-value assertions (lines 50–54). Run it; it should fail to compile (no `Forgejo` member yet).
- [x] [test] In `core/test/ConfigTest.scala`, add a `toHocon` test: a Forgejo config (`trackerType = Forgejo`, `repository`, `baseUrl`) serializes to HOCON containing `type = forgejo` plus `repository` and `baseUrl`. Mirror "ConfigSerializer serializes GitHub config to HOCON" (lines 121–131). Use `ProjectConfiguration.create` (lines 153–172) to build the config.
- [x] [test] In `core/test/ConfigTest.scala`, add a `fromHocon` test: HOCON with `type = forgejo`, `repository`, `baseUrl` (+ optional `teamPrefix`) parses into `IssueTrackerType.Forgejo` with the expected fields. Mirror "ConfigSerializer deserializes HOCON with GitHub tracker" (lines 133–149).
- [x] [test] In `core/test/ConfigTest.scala`, add a round-trip test: `toHocon` → `fromHocon` preserves `trackerType`, `repository`, `baseUrl`, `teamPrefix`, and `projectName` for a Forgejo config. Mirror "ConfigSerializer round-trip for GitHub config" (lines 151–166).
- [x] [test] In `core/test/ConfigTest.scala`, add a `repository` format-validation test: a Forgejo config with a malformed `repository` (e.g. `"invalid"`) returns `Left` with the existing "repository must be in owner/repo format" message — proving reuse of the shared predicate.
- [x] [test] In `core/test/ConfigTest.scala`, add a `baseUrl` scheme-validation test: a `type = forgejo` config with a non-http(s) `baseUrl` returns `Left`, mirroring "ConfigSerializer rejects trackerBaseUrl with non-http(s) scheme" (lines 224–241) — proving the shared `baseUrl` predicate covers Forgejo.
- [x] [test] Add the presence-behaviour test matching the Setup decision. **Option A (default):** a Forgejo config missing `repository` (and/or `baseUrl`) parses successfully with those fields as `None`, mirroring the GitHub precedent "ConfigSerializer succeeds when GitHub config missing repository" (lines 168–182). (Option B: instead assert missing `repository`/`baseUrl` returns `Left`.)
- [x] [test] Run `./iw ./test unit` and confirm the new tests fail for the right reason (compile error / `Unknown tracker type: forgejo`), not an unrelated failure.

## Implementation

- [x] [impl] In `core/model/Constants.scala`, add `val Forgejo = "forgejo"` to `object TrackerTypeValues` (lines 49–53).
- [x] [impl] In `core/model/Config.scala`, add `Forgejo` to `enum IssueTrackerType` (line 103): `case Linear, YouTrack, GitHub, GitLab, Forgejo`.
- [x] [impl] In `core/model/Config.scala`, add the `IssueTrackerType.Forgejo => Constants.TrackerTypeValues.Forgejo` arm to `ConfigSerializer.toHocon`'s `trackerType match` (lines 205–209). The generic `trackerFields` emission below (lines 213–218) needs no edit.
- [x] [impl] In `core/model/Config.scala`, add the `Constants.TrackerTypeValues.Forgejo => Right(IssueTrackerType.Forgejo)` arm to `ConfigSerializer.fromHocon`'s `trackerTypeStr match` (lines 240–249), before the `other` fallthrough. The generic field-parsing block (lines 251–316) needs no edit. (If option B was chosen, add the presence guard here as a deliberate, reviewed change.)

## Integration & Verification

- [x] [impl] Compile core with `-Werror`: `scala-cli compile --scalac-option -Werror core/`. Fix any non-exhaustive-match warnings that surface *within Layer 1* (`model/`) only; leave adapter/command/init/doctor match errors for their later phases (they are the intended Phase 3–5 work-list).
- [x] [test] Run `./iw ./test unit` and confirm the full `core.test` suite is green, including all new Forgejo tests in `ConfigTest.scala` and the assertion in `ConstantsTest.scala`.
- [x] [test] Verify acceptance criteria from `phase-01-context.md`: enum case exists; `Constants.TrackerTypeValues.Forgejo == "forgejo"`; `toHocon` emits `type = forgejo`; `fromHocon` parses it to `IssueTrackerType.Forgejo`; round-trip is lossless; shared `repository`/`baseUrl` predicates apply; required-vs-optional behaviour is decided explicitly and covered by a test.
