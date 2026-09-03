# Plan 08: Support Absolute URLs for Manual Requests

## Current Status

**Partially implemented.** Manual endpoints persist an absolute-URL flag, valid HTTP(S) absolute URLs bypass project/gateway bases, and common previously corrupted values are migrated.

## Remaining Gaps and Risks

- Relative URLs are still joined by string concatenation instead of `HttpUrl.resolve()`. A base containing a query, fragment, encoded path, or unusual slashes can produce the wrong URL.
- Legacy repair searches for a second protocol substring. A valid relative path containing `http://` as data can be converted incorrectly.
- The boolean mode and path can disagree; there is no single validated invariant.
- Tests cover helpers but not editing, save/reload, gateway switching, and final request construction through the UI.

## Complete Remediation

1. Represent targets explicitly as either `AbsoluteUrl` or `RelativePath`.
2. Parse absolute input with `HttpUrl`; resolve relative input with `HttpUrl.resolve()` against a validated base.
3. Reject unsupported schemes, missing hosts, and malformed values with actionable messages.
4. Migrate only known corruption patterns where both prefix and embedded URL parse successfully; retain ambiguous originals.
5. Derive mode from validated data instead of independently mutable fields.

## Implementation Steps

- Add a `ManualUrlTarget` model and versioned state converter.
- Centralize resolution for display, requests, cURL, and gateway mode.
- Preserve encoded segments, IPv6, ports, repeated queries, and fragments without decode/re-encode drift.
- Show absolute/relative mode beside the editor.

## Tests and Acceptance

- Cover absolute/relative URLs with IPv6, ports, encoded slashes, repeated queries, and fragments.
- Test bases containing a path, query, or trailing slash.
- Verify gateway mode never changes an absolute target.
- Test ambiguous legacy strings without destructive repair.
- Add a UI/state round-trip test and inspect the final OkHttp URL.
