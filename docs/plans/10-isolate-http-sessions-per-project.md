# Plan 10: Isolate HTTP Sessions per Project

## Current Status

**Substantially implemented, but the global fallback remains.** `HttpClientService` is registered as a project service, owns its cookie jar/calls, clears cookies, and disposes connections when the project closes.

## Remaining Gaps and Risks

- A static no-project singleton still exists, and `getInstance(project)` falls back to it if service lookup fails. This can reintroduce cross-project cookies and calls.
- Tests create independent service objects directly; they do not prove IntelliJ returns one service per project or invokes disposal correctly.
- No partition is defined within a project for local, staging, and production environments.
- Cookie limits and cleanup are not documented; an unbounded jar can grow during long IDE sessions.

## Complete Remediation

1. Remove `defaultInstance`, the no-argument accessor, and silent fallback behavior.
2. Require a live project for every request and fail clearly if its service is unavailable.
3. Add an environment/session key before supporting multiple bases in one project.
4. Bound the cookie jar through expiry cleanup and domain/count limits.
5. Keep cookies memory-only unless a separate secure persistence feature is explicitly designed.

## Implementation Steps

- Make service construction project-only and test it through IntelliJ service fixtures.
- Key cookie stores by environment ID when environments are introduced.
- Ensure Clear Cookies, disposal, and environment deletion affect only their scope.
- Remove expired cookies during save and load operations.

## Tests and Acceptance

- Use two IntelliJ test projects and assert distinct service and cookie-jar instances.
- Verify repeated lookup within one project returns the same service.
- Exercise `Set-Cookie`, explicit precedence, expiration, clear, and disposal through MockWebServer.
- Verify closing project A cannot cancel calls or remove cookies in project B.
- Confirm production code has no global client, cookie jar, or fallback accessor.
