# Plan 03: Prevent Responses from Updating the Wrong Endpoint

## Problem

Async callbacks read mutable `currentEndpoint`, so a response can be displayed and persisted on a newly selected endpoint.

## Implementation

1. Capture endpoint identity, URL, endpoint reference, and request sequence before background execution.
2. Return a handle containing the future and OkHttp `Call` for cancellation.
3. Persist to the captured endpoint; update UI only if it is still selected and the sequence is current.
4. Cancel active calls when the project/tool window is disposed.

## Tests and Acceptance

- A completing after switching to B updates only A.
- Reversed completion order leaves the newest request visible.
- Success, failure, cancellation, and disposal always restore safe UI state.

