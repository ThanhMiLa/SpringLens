# Plan 05: Fix Multipart Detection and Uploads

## Current Status

**Substantially implemented.** The scanner preserves `MULTIPART_FILE`, the UI displays a file picker, request building sends real file parts, MIME types are detected, and missing files fail before network execution.

## Remaining Gaps and Risks

- Multiple files are represented as a delimiter-separated string. Valid paths containing commas, semicolons, or newlines cannot be represented safely.
- File existence is checked before OkHttp reads it, leaving a time-of-check/time-of-use failure window.
- Readability, maximum upload size, directory/symlink policy, and files changed after selection are not handled clearly.
- The current file-picker API is deprecated and produces a build warning.
- cURL multipart output is reconstructed from endpoint values instead of the exact resolved request data.

## Complete Remediation

1. Store multipart selections as a structured list of paths, not a delimited value.
2. Validate regular-file status, readability, and configured size limits immediately before request creation.
3. Keep text, JSON, and file parts as typed entries with stable ordering.
4. Replace the deprecated chooser with the current IntelliJ file chooser API.
5. Generate OkHttp and export commands from the same typed multipart representation.

## Implementation Steps

- Add `MultipartPartModel` with `name`, `kind`, `value/path`, `contentType`, and `enabled`.
- Migrate legacy strings conservatively; keep ambiguous input as one path and request reselection.
- Support MIME override with `application/octet-stream` fallback.
- Surface per-row validation errors without attempting network access.

## Tests and Acceptance

- Scan `MultipartFile`, arrays, collections, optional files, JSON `@RequestPart`, and mixed controllers.
- Test paths containing spaces, Unicode, commas, and apostrophes.
- Verify exact filename, MIME type, ordering, and bytes through MockWebServer.
- Cover unreadable, deleted, directory, symlink, oversized, and empty files.
- Assert OkHttp and cURL represent the same typed parts.
