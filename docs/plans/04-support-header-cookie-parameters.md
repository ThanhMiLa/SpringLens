# Plan 04: Send RequestHeader and CookieValue Parameters

## Problem

The scanner recognizes `HEADER` and `COOKIE`, but the UI and request builder do not send their values.

## Implementation

1. Add dedicated UI sections for header and cookie parameters.
2. Send enabled header parameters through OkHttp validation.
3. Combine cookie parameters into a `Cookie` header with documented precedence over the cookie jar.
4. Apply annotation defaults, validate required values, and omit optional empties.
5. Persist values using type + name and mirror behavior in `CurlBuilder`.

## Tests and Acceptance

- Multiple headers/cookies, defaults, required values, and collisions are covered.
- cURL and OkHttp produce equivalent requests.
- Newline/header injection is rejected.

