# Plan 06: Remove Endpoint State Collisions

## Current Status

**Partially implemented.** `EndpointIdentity` includes module, controller FQN, method signature, HTTP method, and normalized path. Manual endpoints use UUID-based keys, and parameter keys include their type.

## Remaining Gaps and Risks

- `migrateLegacyKeys()` marks schema v2 even when discovery returns no endpoints. A temporary empty scan can make legacy entries unreachable.
- Legacy keys with no match or multiple matches are deleted immediately, causing irreversible data loss.
- Migration is not transactional and provides no backup or diagnostics.
- Renaming a module, controller, method, or route changes identity and leaves orphaned state.
- Manual endpoints are stored in both `manualEndpoints` and `endpoints`, increasing duplication and cleanup risk.

## Complete Remediation

1. Advance the schema only after successful, non-empty discovery and completed migration.
2. Preserve ambiguous/unmatched entries in quarantine until the user resolves or deletes them.
3. Add migration diagnostics and an idempotent migration marker.
4. Use one authoritative collection for manual endpoint state and indexes only for lookup.
5. Clean orphans by age/version, not during an ordinary reload.

## Implementation Steps

- Introduce schema v3 with `legacyEndpoints`, migration status, and optional `lastSeenAt` metadata.
- Build the entire old-to-new mapping before mutating state, then apply it atomically.
- Copy only unique mappings and retain every unresolved entry.
- Add a user-visible recovery/reset action for quarantined state.

## Tests and Acceptance

- Verify duplicate routes across modules/controllers and overloaded methods remain independent.
- Test empty, partial, canceled, and repeated scans without losing state.
- Test unique, ambiguous, unmatched, and already-migrated entries.
- Simulate module/controller renames and verify old state remains recoverable.
- Serialize and reload every schema version to prove migration is idempotent and lossless.
