# Repository Guidelines

## Project Structure & Module Organization

SpringLens is an IntelliJ IDEA plugin for exploring and testing Spring Boot APIs. Production Java code is under `src/main/java/vn/io/codelearning/springapitester/`, grouped by responsibility: `scanner/` discovers Spring endpoints and configuration, `client/` builds and executes HTTP requests, `model/` holds domain types, `ui/` contains Swing UI components, and `state/` persists workspace data. Plugin descriptors, message bundles, and icons live in `src/main/resources/`; `META-INF/plugin.xml` registers the plugin. Put unit tests in the matching package beneath `src/test/java/`. Keep screenshots and documentation assets in `docs/images/`.

## Build, Test, and Development Commands

- `./gradlew test` runs the JUnit suite and generates JaCoCo XML and HTML coverage reports.
- `./gradlew build` compiles, tests, and performs the standard Gradle verification lifecycle.
- `./gradlew buildPlugin` creates the installable ZIP in `build/distributions/`.
- `./gradlew runIde` launches a sandbox IntelliJ instance for manual plugin testing.

Use the checked-in Gradle wrapper. The build requires Java 21; do not rely on a system Gradle installation.

## Coding Style & Naming Conventions

Use Java with four-space indentation and braces on the same line as declarations. Follow existing package names under `vn.io.codelearning.springapitester`. Use `PascalCase` for classes, enums, and UI components; `camelCase` for methods, fields, and local variables; and singular names for model classes such as `EndpointModel`. Keep scanner and PSI-related work out of UI classes, and prefer focused helpers over large mixed-responsibility methods. There is no configured formatter or linter, so match the surrounding file’s imports, layout, and conventions.

## Testing Guidelines

Tests use JUnit 4 (`org.junit.Test` and `Assert`) and are named `*Test.java`; test methods use descriptive `test...` names. Add regression coverage alongside the affected package, especially for URL transformation, request generation, state persistence, and scanner behavior. Run `./gradlew test` before opening a pull request and inspect `build/reports/jacoco/test/html/` when changing logic with meaningful branches.

## Commit & Pull Request Guidelines

Follow the history’s Conventional Commit style: `feat(scanner): support gateway routes`, `fix(client): preserve headers`, or `docs: update README`. Keep each commit focused. Pull requests should explain the user-visible change and implementation notes, link related issues when available, include tests run, and attach screenshots or a short recording for UI changes. Update `README.md`, `CHANGELOG.md`, and plugin metadata when a release-facing behavior or version changes.

## Security & Configuration

Never commit real bearer tokens, passwords, API keys, or local service URLs. Use sanitized fixtures in tests and examples; authentication and HTTP request code must avoid logging credentials.
