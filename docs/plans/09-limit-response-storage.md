# Plan 09: Limit Response Memory and Persistence

## Problem

Responses are read and persisted without size limits, allowing large payloads to cause lag, OOM, and oversized state files.

## Implementation

1. Set configurable preview and persisted snapshot limits.
2. Read through a counted stream, truncate safely at UTF-8 boundaries, and mark previews as truncated.
3. Detect binary content and offer “Save to File” instead of converting it to text.
4. Pretty-print only small JSON, redact sensitive headers, and enforce project quotas.

## Tests and Acceptance

- Small responses retain current behavior.
- Large, chunked, binary, and multibyte responses stay bounded without OOM.
- UI clearly indicates truncation/binary data and documents limits.

