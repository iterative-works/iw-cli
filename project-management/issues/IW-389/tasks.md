# Implementation Tasks: Support Forgejo issue tracker

**Issue:** IW-389
**Created:** 2026-06-24
**Estimated Total:** 17-30 hours

## Phase Index

- [x] Phase 1: Domain, config & serialization (Est: 2-4h) → `phase-01-context.md`
- [x] Phase 2: Forgejo HTTP adapter — issue read + create (Est: 5-8h) → `phase-02-context.md`
- [x] Phase 3: Capability wiring + command dispatch/auth (Est: 3-6h) → `phase-03-context.md`
- [x] Phase 4: Init + doctor integration + smoke/harness coverage (Est: 3-5h) → `phase-04-context.md`
- [x] Phase 5: PR creation + CI-check polling (forge parity) (Est: 4-8h) → `phase-05-context.md`

## Notes

- Phase context files generated just-in-time during implementation
- Use wf-implement to start next phase automatically
- Estimates are rough and will be refined during implementation
- Phases follow layer dependency order (domain → infrastructure → application → presentation); a single phase may merge multiple layers when each alone is below the phase-size floor
- Scope is full forge parity (resolved option c): Phases 1-4 deliver issue read/create end-to-end; Phase 5 extends the adapter and `ForgeType`/PR-creation paths so `phase-pr` / `phase-merge` work against Forgejo
- Transport is direct HTTP via sttp; auth via `FORGEJO_API_TOKEN`; config uses `repository` + required `baseUrl`
