# Plan 05: Fix Multipart Detection and Uploads

## Problem

The scanner converts `MULTIPART_FILE` to `FORM_DATA`, so scanned files are sent as text instead of uploads.

## Implementation

1. Preserve `ParamTypeEnum.MULTIPART_FILE` through scanner, UI, and request builder.
2. Distinguish file parts from text/JSON `@RequestPart` values.
3. Show a file picker automatically and reject missing files before network access.
4. Detect MIME types, support multiple files structurally, and remove the silent `dummy.txt` fallback.
5. Keep `HttpRequestBuilder` and `CurlBuilder` multipart semantics identical.

## Tests and Acceptance

- Scanning preserves file type.
- Real files include correct filename, MIME type, and bytes.
- Missing files fail early; mixed file/text/JSON multipart works.

