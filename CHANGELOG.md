<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# SpringLens Changelog

## [1.2.2] - 2026-09-19

### Changed
- **Endpoint-Aware Gateway Selection:** The Direct Services/API Gateway selector now evaluates the selected endpoint against Gateway route targets and `Path` predicates, rather than treating every endpoint in a Gateway project as externally routable.
- **Stable Internal API Controls:** Internal APIs retain the connection selector to prevent layout movement, but expose only **Direct Services**. Public APIs continue to offer both **Direct Services** and **API Gateway**.

### Fixed
- **Internal API Gateway Fallback:** Endpoints without a matching Gateway route no longer receive a guessed Gateway URL and are always sent directly to their owning service.
- **Context-Path Gateway Matching:** Gateway route matching now combines a service `context-path` with its controller endpoint path, correctly recognizing routes such as `/post/**` for a service served at `/post`.
- **Gateway Route Transform Resolution:** Route matching and URL generation now consistently account for `StripPrefix`, `PrefixPath`, and supported `RewritePath` configurations.

## [1.2.1] - 2026-09-18

### Changed
- **Live Request URL Synchronization:** Enabled query parameters with non-empty values now appear immediately in the request URL. Existing manual query parameters and URL fragments are preserved, while parameter names and values are encoded safely without duplicate entries or trailing separators.
- **Flexible Path Variable Input:** Path variables can now be supplied either through the Params table or by replacing the placeholder directly in the URL. Once a placeholder is replaced in the URL, its redundant Params row is hidden and restored automatically if the placeholder is added back.

### Fixed
- **EDT-Safe Spring Configuration Resolution:** Eliminated IntelliJ `Slow operations are prohibited on EDT` errors when selecting endpoints after configuration cache invalidation. Spring configuration and file-index scanning now run in cancellable background smart read actions, while the UI consumes precomputed per-module metadata without blocking the event dispatch thread.
- **Stale Configuration Scan Protection:** Added generation-aware configuration caching and reload guards so results from superseded background scans cannot overwrite newer configuration or endpoint data.
- **Spring Security Parameter Detection:** Parameters injected through `@AuthenticationPrincipal` or `@CurrentSecurityContext` are now treated as framework internals instead of being expanded into incorrect query parameters such as `headers`, `claims`, `tokenValue`, `issuedAt`, and `expiresAt`.
- **Resolved Path Variable Validation:** Fixed false `Missing required path variable` errors when a required placeholder had already been replaced directly in the request URL.
- **Idempotent Query URL Assembly:** Prevented query parameters already visible in the URL from being appended again when sending requests or exporting cURL, PowerShell, and Windows CMD commands.

## [1.2.0] - 2026-09-16

### Added
- **Java Source File API Filter:** The Endpoint Navigator can now filter scanned APIs by controller `.java` file. It lists all discovered source files, keeps the selected file after Reload when available, and provides an **All files** option to restore the full API list. Filtering uses the full source path internally, so files with the same name remain distinct.

### Changed
- **Responsive Endpoint Navigator Controls:** Replaced the inactive search field with the Java source-file filter. The filter occupies its own row; **New Collection**, **Clear Data**, and **Reload** now share an equal-width responsive action row, while the Direct Services/API Gateway selector remains separate.

## [1.1.4] - 2026-09-12

### Fixed
- **Deterministic Request Body Selection:** Request body mode is now selected consistently from the endpoint signature. APIs using `@RequestBody` open in **JSON** mode, while `@ModelAttribute`, `@RequestPart`, and multipart file uploads open in **Form-Data** mode. Endpoints without a request body, along with legacy or missing state, safely default to **JSON**.

### Added
- **Per-Endpoint Request Tab Memory:** SpringLens now remembers the most recently viewed request section for every API—**Params**, **Headers**, **Cookies**, **Auth**, or **Body**—and restores it when returning to that endpoint.
- **Persistent Navigation Context:** The remembered request tab is retained for both scanned and manual endpoints across endpoint reloads and IDE restarts. APIs without a prior selection open on **Params** by default.

## [1.1.3] - 2026-09-05

### Changed
- **Developer-Friendly Data Persistence:** SpringLens now stores and displays request credentials, cookies, and response data verbatim in project state. It no longer uses IntelliJ Password Safe or operating-system keyrings, so opening the tool window does not trigger a credential-permission prompt. Credentials stored only by previous Password Safe versions must be entered again.

## [1.1.2] - 2026-09-03

### Added
- **Isolated HTTP Sessions per Project:** Bound `HttpClientService` and in-memory RFC 6265 cookie jars to individual IntelliJ projects, preventing cross-project session leakage and state pollution.
- **Multi-Platform Request Export:** Added hardened export options for cURL (POSIX-compliant shell quoting), Windows PowerShell, and Windows CMD, with full support for multi-value headers and repeated query parameters.
- **Absolute URL & Gateway Routing for Manual Endpoints:** Enabled direct entry of absolute URLs (`http://`, `https://`) for manual endpoints, bypassing direct base URL prefixing while maintaining gateway routing resolution.
- **Deterministic Spring Config Resolution:** Introduced `SpringConfigResolutionService` supporting multi-document YAML, active profile matching (`spring.profiles.active` with comma-separated and list formats), placeholder interpolation (`${...}` with fallback defaults), and `spring.config.import`.
- **Multipart File Upload Validation:** Added pre-flight file existence checks, read permission validation, and a 100MB file size safeguard for multipart requests.
- **Schema v3 State Keys:** Upgraded endpoint state identification to include HTTP method, module name, and path to prevent key collisions across controllers.

### Changed
- **Unified Manual Endpoint Restoration:** Refactored manual endpoint loading to delegate to `state.restoreEndpoint()`, ensuring full parity with scanned endpoints for TLS consent and parameter restoration.
- **Streamlined Response Reading:** Removed wasteful 10MB drain loops when responses lack `Content-Length` headers, switching to immediate truncation marking and graceful size indication.
- **Removed Deprecated Legacy Config Reader:** Eliminated legacy `SpringBootConfigReader` fallbacks in endpoint scanners and UI to prevent non-deterministic base URL and port calculations.

### Fixed
- **Async Race Conditions & Stale State Overwrites:** Bound asynchronous responses strictly to originating request executions (`RequestExecutionContext`), dropping superseded/canceled responses and eliminating duplicate history entries.
- **Data Loss Prevention on Empty Scans:** Added guards to `pruneOrphanScannedEndpoints` and scan handlers to avoid clearing persisted state and credentials during background indexing or empty scan cycles.
- **Context-Path Retention on Reload:** Fixed context-path loss during project reload by invalidating resolution caches and applying relaxed property bindings.
- **Corrupted URL Sanitization:** Upgraded URL building to use RFC 3986 `HttpUrl.resolve` and preserved nested URLs inside query parameters (e.g. proxy endpoints).
- **False-Positive Binary Response Detection:** Refined binary payload detection to prevent UTF-8 and textual MIME responses with multibyte characters from being incorrectly treated as binary.
- **Safe Truncated File Saving:** Warn users before saving truncated response bodies to disk and cleanly stripped internal UI truncation banners.
- **Developer-Friendly Credential Persistence:** Stores request credentials, cookies, and response data verbatim in project state without Password Safe or operating-system keyring prompts.

### Security
- **Strict Host-Level TLS Consent:** Enforced explicit user consent dialogs for self-signed certificates restricted to local development hosts (`localhost`, `127.0.0.1`, `::1`), and removed deprecated boolean bypass methods.
- **Visible Development Data:** Keeps tokens, passwords, cookies, and response headers visible for development and testing workflows. Project-state files can therefore contain credentials and must not be shared when they are real.

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
