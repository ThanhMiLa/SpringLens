# Plan 11: Make Config and Gateway Scanning Deterministic

## Current Status

**Incomplete and not fully integrated.** A central service, deterministic sorting, generated-file exclusion, placeholder diagnostics, caching, and VFS invalidation exist. Gateway callers delegate to it when available.

## Remaining Gaps and Risks

- `SpringEndpointScanner` and the Tool Window still use legacy `SpringBootConfigReader` results for endpoint and default URLs. The new service mainly supplies a tooltip, so legacy resolution still affects requests.
- Every `application-*.properties` and profile-named YAML file is applied regardless of the active profile.
- Multiple active profiles such as `dev,local` are treated as one string and do not match documents correctly.
- `spring.config.import`, profile groups/include, environment precedence, command-line overrides, and config-tree sources are unsupported.
- Gateway properties routes are not parsed; YAML route placeholders are inconsistent; the first gateway module is selected without deterministic module ordering or ambiguity diagnostics.
- YAML is parsed into generic maps instead of the planned safe typed model.
- Tests cover static helpers only, not real multi-module files, cache invalidation, or UI integration.

## Complete Remediation

1. Make `SpringConfigResolutionService` the only production entry point; reduce legacy readers to adapters or remove them.
2. Implement a documented source model covering module scope, deterministic locations, base/profile files, multi-document activation, and imports.
3. Parse active profiles into an ordered set and apply only active profile files.
4. Resolve placeholders after merging, preserving unresolved values and source diagnostics.
5. Parse Gateway YAML and properties into typed route/predicate/filter models and report unsupported forms.
6. Return immutable results with value provenance, warnings, profiles, and fallback state.

## Implementation Steps

- Introduce typed `ConfigSource`, `ResolvedProperty`, `ServerConfig`, and `GatewayConfig` models.
- Collect and sort sources by explicit precedence, then process bounded imports with cycle detection before merging.
- Select gateway modules deterministically or return an ambiguity requiring user selection.
- Update scanner, Tool Window, detail panel, and gateway calculator to consume one resolved snapshot.
- Invalidate affected module caches on create, edit, move, delete, profile, and imported-file changes.

## Tests and Acceptance

- Use IntelliJ fixtures with real properties, YAML, multi-document YAML, malformed files, imports, and multiple modules.
- Cover zero, one, and multiple active profiles; inactive profile files must never override values.
- Verify each resolved value/fallback has correct source provenance and diagnostics.
- Test Gateway short/expanded syntax, common filters, placeholders, properties format, and ambiguous modules.
- Edit and delete files through VFS; assert cache invalidation and UI/request URL changes.
- Assert no production URL path calls the legacy nondeterministic reader.
