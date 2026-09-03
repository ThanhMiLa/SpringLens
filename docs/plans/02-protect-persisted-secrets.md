# Plan 02: Protect Persisted Secrets and Data

## Problem

Auth credentials, headers, bodies, and responses are stored as plaintext in `spring-lens-state.xml`.

## Implementation

1. Remove credential values from `EndpointSavedState`; keep only an opaque credential ID.
2. Store tokens, passwords, and API keys in IntelliJ PasswordSafe, scoped by project and endpoint identity.
3. Add a `state/CredentialStore` for access, deletion, and legacy migration.
4. Redact Authorization, Cookie, Set-Cookie, and configurable secret headers before persistence.
5. Add a “Persist response history” option with a safe default and make Clear All Data delete credentials too.

## Tests and Acceptance

- Serialized state contains no test token or password.
- Legacy state migrates and is rewritten cleanly.
- Credentials are isolated and fully deleted by clear actions.
- Secrets never appear in logs, exceptions, or snapshots.

