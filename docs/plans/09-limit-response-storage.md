# Plan 09: Limit Response Memory and Persistence

## Current Status

**Partially implemented.** Response previews are capped, UTF-8 truncation is handled, binary data is detected, JSON pretty-printing is bounded, persisted snapshots have a limit, and response headers are redacted.

## Remaining Gaps and Risks

- “Save Response to File” writes only the in-memory preview. A binary response larger than the preview cap is silently saved as a truncated or corrupt file.
- Preview and persistence limits are constants rather than configurable settings.
- Unknown-length responses are drained up to an additional limit only to estimate size; totals remain inaccurate for larger streams and bandwidth is wasted.
- Project quota uses Java character counts instead of UTF-8 bytes and evicts entries in nondeterministic `HashMap` order.
- Response body redaction is unavailable when persistence is enabled.

## Complete Remediation

1. Separate preview and download modes. Preview stops at its cap; download streams directly to a selected temporary file.
2. Never present preview bytes as a complete response. Disable full-save for truncated data or label a separate “Save Preview” action.
3. Add project settings for preview, entry, and total limits with safe bounds.
4. Track persisted byte size and update time; evict deterministically using oldest-first or LRU.
5. Stop reading immediately after truncation unless the user starts a download.

## Implementation Steps

- Add a streaming download API with progress, cancellation, and final byte count.
- Store `persistedBytes` and `responseUpdatedAt` metadata.
- Enforce quota before committing state and remove complete oldest snapshots until under quota.
- Add optional JSON-field redaction patterns for persisted text.

## Tests and Acceptance

- Cover small, large, chunked, compressed, binary, malformed UTF-8, and unknown-length bodies.
- Verify preview memory stays bounded and unknown streams are not drained.
- Download a large binary response and compare its complete checksum with the server payload.
- Test quota using multibyte UTF-8 and deterministic eviction order.
- Verify cancellation removes partial files and existing destinations are not overwritten unexpectedly.
