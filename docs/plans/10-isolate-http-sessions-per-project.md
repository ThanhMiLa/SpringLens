# Plan 10: Isolate HTTP Sessions per Project

## Problem

The JVM-wide client owns one cookie jar, so projects calling the same host may reuse each other’s sessions.

## Implementation

1. Convert `HttpClientService` into an IntelliJ project service.
2. Give each project its own client and `InMemoryCookieJar`.
3. Implement `Disposable` to cancel calls and evict connections when a project closes.
4. Add project-scoped Clear Cookies and do not persist session cookies by default.
5. Partition sessions by environment if multiple environments are introduced.

## Tests and Acceptance

- Two projects calling localhost do not share cookies.
- Same-project endpoints retain expected cookie behavior.
- Clear and disposal release only current-project resources.

