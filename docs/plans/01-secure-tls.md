# Plan 01: Restore Secure TLS Validation

## Problem

`HttpClientService` trusts every certificate and hostname for every host, exposing HTTPS traffic to MITM attacks.

## Implementation

1. Remove the unsafe trust manager and hostname verifier from the default client.
2. Keep a separate insecure client only for explicitly confirmed localhost/loopback development use.
3. Add `allowInsecureTls` with default `false` and a warning/confirmation in `EndpointDetailPanel`.
4. Never log or persist certificates or TLS secrets.

## Tests and Acceptance

- Valid certificates work; self-signed certificates fail by default.
- Hostname mismatches remain rejected in secure mode.
- Insecure mode is opt-in, local-only, and never propagated silently.
- The default path contains no `hostnameVerifier(... -> true)` or empty global trust manager.

