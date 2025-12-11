# Architecture Comparison: Three Scala-CLI Approaches

## Executive Summary

After prototyping three approaches, **Approach 3 (Hybrid with LLM Metadata)** is recommended for iw-cli. It best addresses the core requirements: AI-friendly extensibility, single-file command creation, and excellent discoverability.

## Requirements Recap

| Requirement | Priority |
|-------------|----------|
| AI can generate commands on the fly | Critical |
| Multiple command sources (`.iw/commands`, `~/.config/iw/commands`) | High |
| Shared code (issue tracker, config parsing) | High |
| LLM discoverability (docs, index, help) | High |
| Acceptable performance (<2s cached) | Medium |

## Approach Comparison

### Performance

| Metric | Approach 1 | Approach 2 | Approach 3 |
|--------|-----------|-----------|-----------|
| Cold start | 4.5s | 2-3s | 2-3s |
| Cached run | **2-2.5s** | **0.6s** | **<1s** |
| Binary | N/A | Instant | N/A |

**Winner**: Approach 2 (cached), but Approach 3 is acceptable

### LLM Discoverability

| Feature | Approach 1 | Approach 2 | Approach 3 |
|---------|-----------|-----------|-----------|
| `--list` | grep-based, fragile | Directory listing | **Structured output** |
| `--describe` | None | None | **Full documentation** |
| Index file | None | None | **commands.index.md** |
| Self-documenting | Poor | Poor | **Excellent** |

**Winner**: Approach 3 by a wide margin

### Dynamic Command Creation (by AI)

| Aspect | Approach 1 | Approach 2 | Approach 3 |
|--------|-----------|-----------|-----------|
| Files to create | 1 file | 2+ files + directory | **1 file** |
| Template complexity | Low | Medium (project.scala) | **Low** |
| Registration needed | None | None | None |
| Metadata format | Ad-hoc | None | **Structured header** |

**Winner**: Approach 3 (single file with clear conventions)

### Shared Code

| Aspect | Approach 1 | Approach 2 | Approach 3 |
|--------|-----------|-----------|-----------|
| Mechanism | `//> using file` | `//> using file` in project.scala | Core files on CLI |
| Recompilation | Per command | Per command | Per command |
| Boilerplate | In each file | In project.scala | None (bootstrap handles) |

**Winner**: Approach 3 (cleanest for command authors)

### Multiple Command Sources

All approaches can support multiple directories. Approach 3 makes it easiest:

```bash
# Approach 3 bootstrap can scan multiple locations:
for dir in .iw/commands ~/.config/iw/commands; do
  scan_commands "$dir"
done
```

## Detailed Analysis

### Approach 1: Script Discovery
**Status: NOT RECOMMENDED**

The 2-2.5 second cached startup time is unacceptable for a CLI tool. Users expect sub-second response for simple operations like `iw --version`.

### Approach 2: Git-Style
**Status: VIABLE BUT SUBOPTIMAL**

Pros:
- Best cached performance (0.6s)
- True independence - each command is its own project
- Can package as binaries for instant startup

Cons:
- **AI must create a directory + 2 files per command** - friction for dynamic creation
- No built-in discovery mechanism beyond `ls iw-*/`
- Harder for AI to understand conventions

### Approach 3: Hybrid with LLM Metadata
**Status: RECOMMENDED**

Pros:
- **Single-file commands** - trivial for AI to create
- **Triple discovery system** (`--list`, `--describe`, `commands.index.md`)
- **Self-documenting headers** - pattern is obvious from any command
- Acceptable performance (<1s cached)
- Cleanest shared code mechanism (bootstrap passes core files)

Cons:
- Slightly slower than Approach 2 (0.6s vs <1s)
- Scala-cli warnings need filtering
- Index file needs manual maintenance (or auto-generation)

## Recommendation

**Implement Approach 3 with these enhancements:**

1. **Add warning suppression** - Filter scala-cli output in bootstrap
2. **Add auto-index generation** - `iw index-update` command
3. **Support binary compilation** - For performance-critical commands
4. **Adopt structured headers** from the start:

```scala
// PURPOSE: Brief description
// USAGE: iw cmd [args]
// ARGS:
//   --flag: Description
// EXAMPLE: iw cmd example
```

## Migration Path for Complex Commands

If we later need Approach 2's benefits for specific commands:

1. Start with Approach 3 for all commands
2. If a command needs complex dependencies, move to directory structure
3. Bootstrap can handle both patterns:
   - `commands/foo.scala` → single-file command
   - `commands/foo/` → directory-based command

## Conclusion

Approach 3 best serves the stated requirements:

| Requirement | How Approach 3 Addresses It |
|-------------|---------------------------|
| AI generates commands | Single file with clear pattern |
| Multiple sources | Bootstrap scans multiple directories |
| Shared code | Core files passed automatically |
| LLM discoverability | --list, --describe, commands.index.md |
| Performance | <1s cached (acceptable for dev tool) |

The performance difference vs Approach 2 (0.6s vs <1s) is negligible compared to the massive improvement in LLM discoverability and ease of dynamic command creation.
