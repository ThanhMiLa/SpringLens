# Plan 03: Prevent Responses from Updating the Wrong Endpoint

## Current Status

**Substantially implemented.** Requests capture their endpoint reference and sequence. Completion persists to the originating endpoint and updates visible UI only when the endpoint remains selected and the request is latest. Request handles support cancellation.

## Remaining Gaps and Risks

- Unit tests validate `RequestExecutionTracker`, but not the Swing panel, pooled thread, EDT callback, persistence, and disposal together.
- Cancellation and build failures are not tested through the same lifecycle as successful responses.
- Tracking falls back to object identity when an endpoint ID is absent; canonical `EndpointIdentity` should be used consistently.
- Button and status transitions during concurrent requests to different endpoints need explicit behavioral tests.

## Complete Remediation

1. Use the canonical endpoint state key as the tracker key for scanned and manual endpoints.
2. Model execution as an immutable context containing endpoint key/reference, URL, sequence, and request handle.
3. Centralize success, failure, cancellation, and disposal into one idempotent terminal-state method.
4. Update endpoint data independently; update visible UI only when endpoint key and sequence both match.
5. Cancel all handles on panel/project disposal and ignore callbacks after disposal.

## Implementation Steps

- Add a `RequestExecutionContext` record or final class.
- Replace fallback identity strings with `EndpointIdentity.createKey(endpoint)`.
- Track active executions by endpoint key and sequence.
- Ensure cancellation and OkHttp callbacks cannot complete an execution twice.

## Tests and Acceptance

- Build an IntelliJ fixture test with controllable futures and EDT flushing.
- Send A, switch to B, complete A, and assert only A is persisted.
- Send A twice, complete in reverse order, and assert only the newest result is shown and stored.
- Cover build failure, HTTP failure, cancellation, deletion, reload, and panel/project disposal.
- Assert controls return to a usable state after every terminal path.
