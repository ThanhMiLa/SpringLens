# Plan 08: Support Absolute URLs for Manual Requests

## Problem

Manual endpoints always concatenate the default base URL with `path`; reopening an absolute URL can create an invalid URL.

## Implementation

1. Add explicit URL mode and persist either absolute URL or relative path/base.
2. Parse input with `HttpUrl`; scheme/host means absolute mode.
3. Do not apply Gateway/project base URL to manual absolute endpoints.
4. Replace protocol-repairing string replacements with URL-builder logic.
5. Migrate legacy manual values beginning with `http://` or `https://`.

## Tests and Acceptance

- Absolute URLs survive save/reload/restart.
- Relative URLs join exactly once; query, fragment, IPv6, port, and encoding remain intact.
- Gateway mode cannot alter manual absolute URLs.

