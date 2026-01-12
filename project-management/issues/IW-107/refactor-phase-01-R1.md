# Refactoring R1: Inject installation path into prompt content

**Phase:** 1
**Created:** 2026-01-12
**Status:** Complete

## Decision Summary

Real-world testing revealed that while the template file is correctly found from the installation directory (Phase 1 fix), the template's content uses relative paths that assume iw-cli source is in the current directory. When Claude runs from an external project, it looks for `.iw/core/*.scala` and `.iw/commands/*.scala` in the target project - but those files only exist in the iw-cli installation.

## Current State

File: `.iw/commands/claude-sync.scala` (lines 17-35)
```scala
val iwDir = sys.env.get(Constants.EnvVars.IwCommandsDir)
  .map(p => os.Path(p) / os.up)
  .getOrElse(os.pwd / ".iw")
val promptFile = iwDir / "scripts" / "claude-skill-prompt.md"
// ... error handling ...
val prompt = os.read(promptFile)
```

File: `.iw/scripts/claude-skill-prompt.md` (lines 7-10)
```markdown
1. Read and understand:
   - `.iw/core/*.scala` - Core library modules
   - `.iw/commands/*.scala` - Available commands
   - `.iw/config.conf` - Project configuration
```

**Problem:** The `prompt` variable contains relative paths like `.iw/core/*.scala`. When passed to Claude running in request-service, Claude interprets these as relative to request-service, not the iw-cli installation.

## Target State

After reading the prompt, replace relative `.iw/` paths with absolute paths:

```scala
val prompt = os.read(promptFile)
  .replace(".iw/core/", s"$iwDir/core/")
  .replace(".iw/commands/", s"$iwDir/commands/")
```

This transforms the prompt so Claude sees:
```markdown
1. Read and understand:
   - `/path/to/iw-cli/.iw/core/*.scala` - Core library modules
   - `/path/to/iw-cli/.iw/commands/*.scala` - Available commands
   - `.iw/config.conf` - Project configuration  ← stays relative (project's own config)
```

## Constraints

- PRESERVE: The template file must remain unchanged (portable with relative paths)
- PRESERVE: `.iw/config.conf` reference must stay relative (target project's config)
- PRESERVE: All existing E2E tests must pass
- DO NOT TOUCH: Error handling, Claude invocation, skill generation logic

## Tasks

- [x] [impl] [Test] Add E2E test: claude-sync injects absolute paths into prompt (verify Claude receives correct paths)
- [x] [impl] [Refactor] Add path replacement after `os.read(promptFile)` for `.iw/core/` and `.iw/commands/`
- [x] [impl] [Verify] Run all E2E tests, ensure nothing broke
- [ ] [impl] [Verify] Manual test from external project (request-service)

## Verification

- [x] All existing E2E tests pass
- [x] New E2E test verifies path injection
- [ ] Manual test from request-service shows Claude reads iw-cli source correctly
- [x] `.iw/config.conf` still read from target project (not installation)
