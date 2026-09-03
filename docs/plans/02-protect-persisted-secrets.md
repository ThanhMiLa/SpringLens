# Plan 02: Protect Persisted Secrets and Data

## Current Status

**Partially implemented.** Authentication fields and recognized sensitive custom headers are stored in IntelliJ PasswordSafe. Request-body and response-history persistence are disabled by default, and persisted response headers are redacted.

## Remaining Gaps and Risks

- Parameter values are still serialized directly into `spring-lens-state.xml`. This includes `HEADER`, `COOKIE`, and sensitive query parameters such as `Authorization`, `session`, `token`, or `apiKey`.
- Manual endpoint parameters are cloned with plaintext `currentValue`, creating a second plaintext copy.
- Sensitive classification is fixed and name-based. Custom secret names cannot be configured.
- Opt-in response bodies may contain tokens or personal data; only response headers are redacted.
- Existing tests inspect sanitized objects, not the actual XML produced by IntelliJ persistence.

## Complete Remediation

1. Introduce a central `SensitiveValueClassifier` for auth fields, headers, cookies, and parameter names/types.
2. Move sensitive parameter values to PasswordSafe using stable endpoint and parameter identities.
3. Persist only placeholders or empty values in both `paramValues` and `manualParameters`.
4. Add configurable sensitive-name patterns while retaining conservative defaults.
5. Treat response history as sensitive data: require explicit consent and optionally redact configured JSON fields.
6. Make legacy migration transactional: write secrets, verify retrieval, and only then sanitize XML state.

## Implementation Steps

- Extend `CredentialStore.StoredSecrets` with values keyed by `ParamTypeEnum:name`.
- Sanitize parameters through one method used by scanned and manual endpoints.
- Restore secrets only in memory; never copy them into serializable state.
- Delete endpoint and parameter credentials on endpoint deletion and Clear All Data.

## Tests and Acceptance

- Serialize a real `SpringLensState` and assert tokens, cookies, passwords, and API keys are absent from XML.
- Cover scanned/manual endpoints, configurable names, migration failure, deletion, and project isolation.
- Verify redaction is case-insensitive and covers authorization, cookies, proxy auth, and token-like names.
- Verify logs, exceptions, persisted snapshots, and default exports do not expose test secrets.
