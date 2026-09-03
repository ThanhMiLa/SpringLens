# Plan 11: Make Config and Gateway Scanning Deterministic

## Problem

Config readers depend on nondeterministic file order, incompletely apply profiles/imports, and silently swallow parsing errors.

## Implementation

1. Consolidate both config readers behind one resolution service.
2. Scope files to module roots, sort deterministically, and exclude generated files.
3. Implement documented precedence for base/profile/bootstrap files and YAML document activation.
4. Track unresolved placeholders instead of silently using `8080`.
5. Parse YAML with a safe typed model and support common Gateway predicate/filter forms.
6. Return diagnostics with source and fallback; replace empty `catch (Throwable)` paths.
7. Cache per module and invalidate through VFS changes; show source/fallback status in UI.

## Tests and Acceptance

- Multi-module, profile, multi-document, placeholder, malformed YAML, and Gateway cases are deterministic.
- Cache invalidates after edits.
- UI warns when a URL is fallback or uncertain.
