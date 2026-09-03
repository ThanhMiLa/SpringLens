# Plan 04: Send RequestHeader and CookieValue Parameters

## Current Status

**Implemented at the request-builder level, with integration and security gaps.** Dedicated UI tables exist, required/default values are handled, headers are validated, and explicit cookies override cookie-jar values by name.

## Remaining Gaps and Risks

- Sensitive header and cookie parameter values are persisted as plaintext; Plan 02 must protect them.
- Existing tests do not prove the full PSI scanner → UI → OkHttp path for annotation aliases, defaults, and inherited methods.
- Simple cookie parsing may mishandle quoted values or unusual valid cookie characters.
- cURL contains explicit cookie parameters but cannot reproduce cookies injected later by the project cookie jar.

## Complete Remediation

1. Route all parameter values through shared resolution and validation code.
2. Mark sensitive header/cookie parameters and store them through PasswordSafe.
3. Define precedence explicitly: cookie jar, custom `Cookie` header, then `@CookieValue` parameter.
4. Use a structured cookie representation instead of repeatedly parsing raw headers.
5. Offer an explicit “include current session cookies” export option, disabled by default.

## Implementation Steps

- Add scanner fixtures for `name`, `value`, `required`, and `defaultValue` annotation forms.
- Represent explicit cookies as ordered name/value entries until final request construction.
- Reject CR/LF and invalid names before invoking OkHttp.
- Reuse the same resolved request data for OkHttp, cURL, and PowerShell.

## Tests and Acceptance

- Cover multiple values, optional empties, defaults, duplicates, and explicit-versus-jar collisions.
- Verify newline injection is rejected in names and values.
- Assert sensitive values are absent from serialized state and default exports.
- Scan a controller fixture and inspect the final MockWebServer request.
- Verify export behavior clearly states whether session cookies were included.
