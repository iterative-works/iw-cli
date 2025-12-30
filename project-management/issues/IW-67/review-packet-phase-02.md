# Review Packet: Phase 2 - Handle invalid Mermaid syntax gracefully

**Issue:** IW-67
**Phase:** 2 of 3
**Date:** 2025-12-29

## Goals

Enable graceful handling of invalid Mermaid syntax:
1. Configure Mermaid.js error handling to display user-friendly error messages
2. Ensure invalid diagrams show error information with location hints
3. Ensure page remains functional when one diagram has errors
4. Add styling for error messages matching the artifact viewer aesthetic

## Scenarios

| # | Scenario | Verification |
|---|----------|--------------|
| 1 | Invalid Mermaid syntax shows error message | E2E: test-mermaid-errors.md |
| 2 | Error indicates problem location | Mermaid built-in provides this |
| 3 | Page doesn't break with error | E2E: other content renders |
| 4 | Other diagrams still render | E2E: valid diagrams in same file |

## Entry Points

### Modified Files

1. **`.iw/core/presentation/views/ArtifactView.scala`**
   - `mermaidInitScript`: Added `securityLevel: 'loose'` for detailed errors
   - `styles`: Added `.mermaid` and error styling CSS

2. **`.iw/core/test/ArtifactViewTest.scala`**
   - Added 2 tests for error handling configuration

### New Files

1. **`project-management/issues/IW-67/test-mermaid-errors.md`**
   - E2E test file with valid and invalid Mermaid diagrams

## Architecture

```
ArtifactView.scala
├── mermaidInitScript (modified)
│   ├── startOnLoad: true
│   ├── theme: 'neutral'
│   └── securityLevel: 'loose'  <-- NEW
└── styles (modified)
    ├── .mermaid             <-- NEW (base styling)
    ├── .mermaid .error-text <-- NEW (error text fill)
    └── .mermaid:has(...)    <-- NEW (error container)
```

## Test Summary

| Type | Count | Status |
|------|-------|--------|
| Unit (new) | 2 | ✅ Pass |
| Unit (existing) | 18 | ✅ Pass |
| E2E | 1 file | Pending browser verification |

### New Tests

1. `mermaid initialization sets securityLevel to loose for error display`
2. `CSS includes mermaid error styling`

## Changes Summary

### Mermaid Configuration
```javascript
// Before
mermaid.initialize({ startOnLoad: true, theme: 'neutral' });

// After
mermaid.initialize({
  startOnLoad: true,
  theme: 'neutral',
  securityLevel: 'loose'
});
```

### CSS Additions
- `.mermaid`: Base diagram styling (centered, margin)
- `.mermaid .error-text`: Red text fill for errors
- `.mermaid:has(.error-text)`: Red border and pink background for error containers

## Verification Notes

Manual browser verification pending. To test:
1. Start server: `./iw server start`
2. Navigate to: `/artifact/IW-67/test-mermaid-errors.md`
3. Verify:
   - First flowchart renders correctly
   - Invalid syntax shows error message
   - Table and content after error render
   - Sequence diagram renders correctly

## Review Checklist

- [x] Tests added for new functionality
- [x] No regressions (all existing tests pass)
- [x] CSS follows existing patterns
- [x] Changes are minimal and focused
- [ ] E2E browser verification (pending)
