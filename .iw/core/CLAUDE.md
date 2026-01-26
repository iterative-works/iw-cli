# Core Module Guidelines

This directory uses FCIS (Functional Core Imperative Shell) architecture. Follow these rules when working here.

## Where to Place New Code

**Decision tree:**
1. Dashboard server specific? → `dashboard/`
2. Performs I/O (shell, network, files)? → `adapters/`
3. Formats CLI output? → `output/`
4. Pure data/logic? → `model/`

When in doubt: if it does ANY I/O, put it in `adapters/`.

## Directory Rules

### model/ - Pure Domain Types
Place here: value objects, entities, data structures, enums, pure validation.

**Requirements:**
- NO I/O operations
- NO external calls
- NO state mutation
- NO imports from `adapters/`, `output/`, or `dashboard/`

### adapters/ - I/O Operations
Place here: shell commands, API clients, filesystem ops, user prompts.

**May import:** `model/`

### output/ - CLI Presentation
Place here: console formatting, text rendering, CLI display logic.

**May import:** `model/`

### dashboard/ - Server Internals (Internal API)
Place here: HTTP server, services, caches, server state.

**May import:** everything

**Note:** Scripts in `.iw/commands/` should NOT import from `dashboard/`.

## Import Patterns

In `.iw/commands/*.scala`:
```scala
import iw.core.model.*      // Pure domain types
import iw.core.adapters.*   // I/O operations
import iw.core.output.*     // CLI formatting
// Do NOT import iw.core.dashboard.*
```

Within core modules - dependencies flow inward:
```
dashboard/ → adapters/ → model/
     ↓          ↓
   output/ ────┘
```

## Testing

- `model/`: Pure assertions, no mocking, fast tests
- `adapters/`: May need mocking, integration tests for shell commands
- `output/`: String comparison tests
- `dashboard/`: Full integration tests with HTTP

## Common Patterns

**Extracting pure logic:** If a function does I/O AND logic, split it:
- Pure logic → `model/`
- I/O wrapper → `adapters/`

**Adding a new API client:** Goes in `adapters/`, returns `model/` types.

**Adding CLI output:** Format function in `output/`, data types in `model/`.
