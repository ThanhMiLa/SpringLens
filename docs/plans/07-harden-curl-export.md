# Plan 07: Make cURL Export Safe and Accurate

## Current Status

**Partially implemented.** POSIX and PowerShell quoting helpers exist, URLs use `HttpUrl`, sensitive headers/query parameters are redacted by default, and credentials can be explicitly included.

## Remaining Gaps and Risks

- `buildCurl(Request, ...)` does not serialize the actual request body. Without an `EndpointModel`, any body is exported as `{}`.
- Multipart output is rebuilt from mutable endpoint parameters and can drift from the built OkHttp request.
- Redacting repeated sensitive query parameters with `setQueryParameter` collapses duplicate values.
- Sensitive-name matching can miss uncommon credential names and over-redact ordinary names containing `auth`.
- No execution-level test proves that a generated command sends the same request as OkHttp.

## Complete Remediation

1. Create an immutable, typed `ResolvedRequest` before constructing OkHttp or exports.
2. Generate OkHttp, cURL, and PowerShell from that representation.
3. Preserve repeated query parameters and semantically relevant header ordering.
4. Require credential inclusion per export and never persist that choice.
5. For arbitrary OkHttp bodies, serialize to a bounded buffer and reject one-shot or duplex bodies clearly.

## Implementation Steps

- Add body variants for none, text/JSON, form, and multipart.
- Replace the public `Request` overload or make its body serialization accurate.
- Redact query values by index rather than replacing all values by name.
- Keep independent POSIX and PowerShell renderers.

## Tests and Acceptance

- Cover apostrophes, spaces, newlines, `$`, backticks, Unicode, fragments, repeated queries, and shell payloads.
- Test JSON, empty, DELETE, form, and multipart bodies.
- Execute generated cURL against MockWebServer when cURL is available and compare method, URL, headers, and bytes.
- Verify PowerShell output structurally on all platforms.
- Assert default exports contain no test secrets and inclusion always requires an explicit action.
