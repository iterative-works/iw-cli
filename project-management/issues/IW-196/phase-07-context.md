# Phase 7: Fix remediation via Claude Code

## Goal

Add `iw doctor --fix` that launches a Claude Code session with a dynamically generated prompt to remediate failing quality gate checks. The prompt includes the list of failed checks, detected build system, and CI platform.

## Scope

### In Scope
- `--fix` flag parsing in doctor command
- Build system detection (`build.mill` → Mill, `build.sbt` → SBT, `project.scala` → scala-cli)
- CI platform detection (from tracker type)
- Prompt generation from failed quality checks
- Launching `claude --print-cost -p "..."` process
- No-op when all quality checks pass
- Unit tests for prompt generation and build system detection
- E2E test for no-fix-needed case

### Out of Scope
- Testing the actual Claude Code output (non-deterministic)
- Per-check fix granularity
- Interactive fix mode

## Dependencies

- Phases 1-6 (complete): All quality gate checks and filtering

## Approach

1. **Build system detection** in `core/model/BuildSystem.scala`:
   - `enum BuildSystem { case Mill, SBT, ScalaCli, Unknown }`
   - `detectWith(fileExists: os.Path => Boolean): BuildSystem`

2. **Prompt generation** in `core/model/FixPrompt.scala`:
   - Pure function: `generate(failedChecks: List[String], buildSystem: BuildSystem, ciPlatform: String): String`
   - Assembles a detailed prompt for Claude Code

3. **Update doctor.scala**:
   - Parse `--fix` flag
   - Run quality checks only
   - Collect failures
   - Detect build system and CI platform
   - Generate prompt
   - Launch `claude --print-cost -p "prompt"` via ProcessAdapter

## Files to Create

- `.iw/core/model/BuildSystem.scala` - Build system enum and detection
- `.iw/core/model/FixPrompt.scala` - Prompt generation
- `.iw/core/test/BuildSystemTest.scala` - Unit tests
- `.iw/core/test/FixPromptTest.scala` - Unit tests

## Files to Modify

- `.iw/commands/doctor.scala` - Add `--fix` flag handling
- `.iw/test/doctor.bats` - Add E2E tests

## Testing Strategy

### Unit Tests
- Build system detection: Mill, SBT, scala-cli, Unknown
- Prompt includes failed check names
- Prompt includes build system name
- Prompt includes CI platform
- Empty failed checks returns empty/no-fix message

### E2E Tests
- `iw doctor --fix` with all checks passing → "Nothing to fix" message
- `iw doctor --fix` format check (verify command structure without executing Claude)

## Acceptance Criteria

- [ ] `iw doctor --fix` launches Claude Code with remediation prompt
- [ ] Prompt includes detected build system, CI platform, and list of failures
- [ ] No-op when all quality checks pass
- [ ] Build system detection works for Mill, SBT, scala-cli
- [ ] Unit tests verify prompt generation
- [ ] E2E test verifies no-fix-needed case
