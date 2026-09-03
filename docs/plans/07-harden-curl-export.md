# Plan 07: Make cURL Export Safe and Accurate

## Problem

Raw URL, query, header, and multipart values are concatenated into shell strings without complete quoting or encoding.

## Implementation

1. Serialize cURL from the built OkHttp request to avoid behavioral drift.
2. Use one POSIX shell-quoting helper for every argument and reject CR/LF in headers.
3. Merge/encode queries with `HttpUrl`; escape filenames and form values.
4. Add “Include credentials”, redact sensitive headers by default, and implement separate PowerShell support if needed.

## Tests and Acceptance

- Quotes, spaces, `$`, backticks, newlines, Unicode, existing queries, and JSON apostrophes are safe.
- cURL matches OkHttp method, URL, headers, and body.
- Injection tests cannot append another shell command.

