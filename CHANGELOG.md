<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# SpringLens Changelog

## [1.1.1] - 2026-09-01

### Fixed
- **Gateway Endpoint Navigation & Lag:** Fixed single-URL fixation and UI freezes when selecting endpoints in Gateway mode by caching `GatewayConfig` in memory and resolving routing asynchronously.
- **Constant Array Resolution for Public APIs:** Upgraded AST scanner (`SecurityConfigScanner`) to recursively resolve variable references such as `String[] PUBLIC_ENDPOINTS`, `List.of(...)`, and `Arrays.asList(...)` in `requestMatchers(...).permitAll()`.
- **IntelliJ 2025.2+ Threading Assertions:** Wrapped project file index access in `runReadAction` to prevent background thread assertion exceptions during endpoint reload.
- **Microservices Routing Matching:** Added case-insensitive matching for microservice route IDs, module names, and support for `lb://` service discovery URIs.

## [1.1.0] - 2026-09-01

### Added
- **Interactive Security Lock Badges:** Added visual lock/unlock status indicators in the Endpoint Navigator, allowing developers to manually toggle an endpoint's security requirement with a single click.
- **Response History & Snapshot Persistence:** Automatically preserves and restores the latest HTTP response body, status code, and latency metrics across IDE restarts for seamless workflow resumption.
- **Global Cache Management:** Added a dedicated **"Clear Cache"** action to reset all stored response history, temporary inputs, and workspace cache instantly.

### Changed
- **Enhanced UI Showcase & Media Assets:** Refreshed high-resolution screenshots and optimized animated demo GIF.
- **Responsive Description Layout:** Optimized overview formatting for 100% responsiveness across all IntelliJ IDE window sizes and theme modes.

### Fixed
- **Intelligent Endpoint Security Detection:** Refined AST/PSI static code analysis algorithms to accurately classify Public vs. Protected/Private endpoints based on Spring Security configurations and method annotations.

## [1.0.0] - 2026-08-30

### Added
- **Smart AST Endpoint Scanner:** Automatically discovers all Spring Boot REST endpoints across all project modules.
- **DTO Schema Generator:** Instant dummy JSON template generation from Java DTO classes with recursive loop protection and smart merge.
- **Spring Cloud Gateway Support:** Intelligent URL calculation for microservices routing with `StripPrefix`, `PrefixPath`, and `context-path` retention.
- **Authentication Manager:** Bearer Token, Basic Auth, and API Key support with one-click **"Apply to All APIs"** global synchronization.
- **Postman-Style Native UI:** Soft word wrapping, colorful HTTP method badges, response formatting, and cURL export.
- **Trust-All SSL for Dev:** Seamless local HTTPS testing without `SSLHandshakeException` errors.
- **State Persistence:** Automatically preserves parameter inputs and testing history across IDE restarts.

