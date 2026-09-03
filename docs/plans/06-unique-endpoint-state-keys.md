# Plan 06: Remove Endpoint State Collisions

## Problem

State keys contain only method and path, so identical routes across modules/controllers overwrite one another.

## Implementation

1. Create `EndpointIdentity` from module, controller FQN, method signature, method, and normalized path.
2. Use persisted UUIDs for manual endpoints and centralize key creation.
3. Add state schema versioning and migrate old entries only when mapping is unambiguous.
4. Include parameter type in parameter keys and clean orphaned state after manual edits.

## Tests and Acceptance

- Same route in different modules/controllers retains independent state.
- Overloads and multi-method/path mappings are distinct.
- Versioned migration never assigns ambiguous data.

