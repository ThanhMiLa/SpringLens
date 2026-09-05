# Plan 12: Remove credential redaction and Password Safe persistence

## Goal

Make SpringLens behave as a developer-focused API testing tool: request and
response data must be displayed and persisted exactly as entered or received.
The plugin must not access IntelliJ Password Safe, macOS Keychain, Windows
Credential Manager, or a Linux keyring. Opening SpringLens must therefore not
trigger an operating-system credential prompt.

## Product decision

SpringLens will favour convenience and reproducibility in development
environments over encrypted credential persistence.

- Bearer tokens, Basic Auth passwords, API keys, custom headers, and parameter
  values are saved directly in the project state.
- Request and response values are never replaced with an empty value or
  `[REDACTED]` by SpringLens.
- Session cookies received from responses remain in memory for the current
  project/IDE session only. They continue to be cleared on project disposal or
  when the user selects **Clear Session Cookies**.
- Users are responsible for not committing or sharing project-state files that
  contain real credentials.

## Scope

### Remove

- All `PasswordSafe` and IntelliJ credential-store reads/writes.
- `CredentialStore` and its credential migration/fallback behaviour.
- Sanitisation that clears sensitive auth, headers, or parameter values before
  they are persisted.
- Response-header and JSON-body redaction.
- UI output that replaces response header values with `[REDACTED]`.

### Keep

- Auth configuration and request execution behaviour.
- Automatic in-memory cookie handling in `InMemoryCookieJar`.
- Response history limits and response-body size limits.
- The ability to clear session cookies explicitly.

## Implementation plan

### 1. Persist endpoint data directly

Update `SpringLensState` so saving an endpoint writes the values already held
by the endpoint model directly to `EndpointSavedState`:

- Persist all `AuthConfig` fields, including bearer token, Basic Auth username
  and password, and API-key value.
- Persist all custom header values, including `Authorization`, `Cookie`,
  `Set-Cookie`, and API-key headers.
- Persist parameter values and manual-endpoint default values without checking
  whether their names look sensitive.
- Restore auth, headers, and parameters directly from saved state without a
  second credential lookup.

Remove the Password Safe-specific members and methods from `SpringLensState`,
including `credentialStoreOverride`, `needsCredentialMigration`,
`fallbackMemoryStore`, `credentialStore`, `storeCredentials`, `loadSecrets`,
`resolveAuthConfig`, `resolveHeaders`, `drainFallbackToPasswordSafe`,
`migrateLegacyCredentials`, and test-only credential-store attachment.

Remove `credentialId` from newly written endpoint state. Keep deserialisation
tolerant of the legacy field so existing workspace XML can still load.

### 2. Stop redacting responses

Update `EndpointDetailPanel.formatResponseHeaders` to append the response
header values exactly as supplied by `HttpResponseModel`.

Update `SpringLensState` response persistence to:

- save `lastResponseHeaders` directly;
- save response-history headers directly; and
- save the response body directly, subject only to the existing size limits.

Remove `redactResponseHeaders` and the use of
`SensitiveValueClassifier.redactSensitiveJson` in response persistence.

### 3. Remove obsolete secret-storage code

Delete `state/CredentialStore.java` after its callers are removed.

Delete `state/SensitiveValueClassifier.java` if no non-redaction caller
remains. Do not retain it merely to classify values that are no longer treated
differently.

Remove unused IntelliJ credential-store imports and any associated Gradle
dependency only if it is not required transitively by the IntelliJ platform.

### 4. Preserve session-cookie semantics

Do not persist the contents of `InMemoryCookieJar`. It is intentionally a
runtime cookie jar populated from `Set-Cookie` response headers and used for
subsequent requests in the active project session.

Cookies that the user explicitly enters in a Cookie parameter or a custom
`Cookie` header are ordinary persisted endpoint data and must be saved without
redaction.

### 5. Legacy data behaviour

Earlier versions moved sensitive values from workspace XML to Password Safe.
Reading those values would require accessing the platform credential store and
could recreate the operating-system prompt this change removes.

Therefore the new version must not automatically migrate, retrieve, delete,
or modify legacy Keychain/credential-store entries. Legacy endpoint fields that
were previously blank must be entered again by the user once. Subsequent saves
store them directly in project state.

The orphaned operating-system credential entries are harmless and can remain;
SpringLens will no longer reference them.

## Tests

Remove or replace tests whose expected behaviour is Password Safe storage or
redaction:

- `CredentialStoreTest`
- Password Safe fixtures/assertions in `SensitiveValueProtectionTest`
- sensitive-cookie expectation in `ParameterPersistenceTest`
- response-header redaction test in `ResponseCachingTest`
- credential purge/setup in `EndpointIdentityAndStateKeyTest`

Add or update regression coverage for these behaviours:

1. An endpoint round-trips Bearer token, Basic Auth password, API-key value,
   and custom `Authorization`/`Cookie` headers through `SpringLensState`.
2. Cookie, query, header, and path parameters whose names include `token`,
   `password`, or `session` persist unchanged.
3. Manual-endpoint sensitive-looking default/current values persist unchanged.
4. `Set-Cookie`, `Authorization`, and token-like response headers display and
   persist unchanged in current response and response history.
5. A JSON response containing token/password/session fields persists unchanged.
6. Production sources have no references to `PasswordSafe`, `CredentialStore`,
   `CredentialAttributes`, or `Credentials`.
7. Cookie jar values still propagate to a later request during the active
   session and are cleared by the existing clear/dispose paths.

## Documentation and release notes

Update the following text with the new policy:

- `README.md`: remove the claim that credentials are stored in IntelliJ
  Password Safe. Add a concise warning that project state can contain
  credentials and must not be committed or shared when those credentials are
  real.
- `src/main/resources/META-INF/plugin.xml`: remove the “Safer Workspace
  Persistence” wording that promises protected credential persistence.
- `CHANGELOG.md`: describe the breaking persistence-policy change, the removal
  of OS credential prompts, and the one-time need to re-enter credentials that
  existed only in legacy Password Safe entries.

## Verification

Run:

```bash
./gradlew test
./gradlew buildPlugin
```

Perform a manual smoke test on macOS:

1. Open a project and the SpringLens tool window; no Keychain prompt appears.
2. Enter a Bearer token, password, API key, Cookie header, and a token-named
   parameter; restart the IDE and confirm all values remain visible.
3. Receive `Set-Cookie` and token-bearing response headers; confirm their real
   values are visible and remain in response history after restart.
4. Confirm automatic session cookies still authenticate subsequent requests;
   use **Clear Session Cookies** and confirm the session is removed.

## Acceptance criteria

- SpringLens makes no call to an operating-system or IntelliJ credential store.
- Opening SpringLens produces no credential-permission prompt.
- The plugin does not emit `[REDACTED]` or blank saved values because a field
  name is sensitive.
- User-entered credentials and response data persist verbatim within the
  existing project-state and response-cache limits.
- Automated tests and `buildPlugin` succeed.
