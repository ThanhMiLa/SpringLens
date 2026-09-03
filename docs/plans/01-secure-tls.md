# Plan 01: Restore Secure TLS Validation

## Current Status

**Substantially implemented, with verification gaps.** The default OkHttp client uses platform trust and hostname validation. An insecure client is created only for localhost or loopback hosts, and the UI requires confirmation before enabling it.

## Remaining Gaps and Risks

- The insecure choice is persisted and restored without showing the warning again. A copied or migrated endpoint can therefore retain insecure TLS longer than the user expects.
- Current tests do not prove that a real certificate mismatch fails or that a valid test certificate succeeds.
- The legacy JVM-wide fallback `HttpClientService.getInstance()` can still create an unscoped client. This weakens the project-service guarantee and should be removed under Plan 10.

## Complete Remediation

1. Keep insecure TLS endpoint-scoped, but store an explicit consent version and normalized target host or make consent session-only.
2. Reconfirm after import, migration, or whenever the target host changes.
3. Enforce the loopback restriction at the HTTP service boundary, regardless of UI state.
4. Remove the global fallback client once all callers use the project service.
5. Avoid logging certificate chains, authorization headers, or bodies from TLS failures.

## Implementation Steps

- Add an `InsecureTlsConsent` value containing the normalized host and policy version.
- Validate the request host against that consent immediately before selecting the unsafe client.
- Clear stale consent when an endpoint URL changes or is imported.
- Replace no-project service access with `HttpClientService.getInstance(project)` only.

## Tests and Acceptance

- Use MockWebServer with a trusted test certificate, a self-signed certificate, and a hostname mismatch.
- Verify secure mode accepts only the trusted matching certificate.
- Verify insecure mode works only for `localhost`, `127.0.0.0/8`, and `::1` after consent.
- Verify reopening, importing, or changing a host does not silently reuse stale consent.
- Confirm production code has no unconditional trust manager or hostname verifier.
