# SpringLens Contributor Guide

SpringLens is an IntelliJ IDEA plugin for discovering and testing Spring Boot APIs. This document is the repository-specific operating guide for contributors and coding agents. Prefer the smallest focused change that satisfies the request, and preserve unrelated work already present in the working tree.

## Repository Map

| Location | Purpose |
| --- | --- |
| `src/main/java/vn/io/codelearning/springapitester/scanner/` | Spring annotations, endpoint discovery, and configuration scanning. |
| `src/main/java/vn/io/codelearning/springapitester/client/` | HTTP request construction, execution, cookies, TLS consent, and responses. |
| `src/main/java/vn/io/codelearning/springapitester/model/` | Request, endpoint, configuration, and other domain models. |
| `src/main/java/vn/io/codelearning/springapitester/ui/` | Swing tool-window and editor components. Keep scanner and PSI work out of this layer. |
| `src/main/java/vn/io/codelearning/springapitester/state/` | Per-project workspace persistence. |
| `src/main/resources/` | Plugin descriptor, messages, and icons; `META-INF/plugin.xml` registers the plugin. |
| `src/test/java/` | JUnit tests, mirroring the production package layout. |
| `docs/images/` | README screenshots and other documentation assets. |
| `.agents/skills/` | Repository-specific instructions for code style and commits. |

## Required Skills

Read the relevant skill before starting the corresponding work:

- For any Java implementation, edit, or review, read and apply `.agents/skills/code-style/SKILL.md`.
- Before preparing or creating a Git commit, read and apply `.agents/skills/commits/SKILL.md`.

The skill instructions supplement this guide. If they conflict, follow the more specific instruction.

## Working Rules

- Inspect the affected code, its tests, and nearby conventions before changing it.
- Keep responsibilities separated: UI coordinates user interaction, scanner code handles PSI/config discovery, client code executes requests, and state code persists data.
- Make focused changes. Do not reformat or alter unrelated files while addressing a task.
- Do not edit generated output under `build/`; regenerate it through Gradle when needed.
- Use the checked-in Gradle wrapper. The project requires JDK 21; do not use a system Gradle installation.

## Java Conventions

Apply the full Java guidance in `.agents/skills/code-style/SKILL.md`. In particular:

- Use four-space indentation and opening braces on the declaration line.
- Use `PascalCase` for types and `camelCase` for methods, fields, and local variables.
- Prefer intent-revealing names and small, cohesive methods. Extract a helper only when it clarifies a meaningful rule, transformation, validation, or side effect.
- Follow the closest existing file for import ordering and layout; remove unused imports.
- Prefer clear code over comments. Add concise Javadoc only for a public API or non-obvious behavior that needs explanation.

## Build, Test, and Manual Verification

| Command | Use it for |
| --- | --- |
| `./gradlew test` | Run the JUnit suite and generate JaCoCo XML and HTML reports. |
| `./gradlew build` | Compile, test, and run the standard verification lifecycle. |
| `./gradlew buildPlugin` | Produce the installable ZIP in `build/distributions/`. |
| `./gradlew runIde` | Launch a sandbox IntelliJ instance for manual plugin checks. |

Tests use JUnit 4 (`org.junit.Test` and `Assert`), are named `*Test.java`, and use descriptive `test...` method names. Add regression tests beside the affected package when behavior changes, especially for URL resolution, request generation/execution, persistence, or endpoint scanning.

Run the narrowest relevant verification while developing, then run `./gradlew test` before a pull request. For meaningful branch changes, inspect the JaCoCo HTML report at `build/reports/jacoco/test/html/`. For UI changes, verify the workflow with `./gradlew runIde` when practical and capture a screenshot or short recording for the pull request.

## Documentation, Releases, and Pull Requests

- Update `README.md` when installation, usage, compatibility, or user-visible behavior changes.
- Update `CHANGELOG.md` and plugin version metadata when preparing a release-facing version change. Keep `gradle.properties` and `src/main/resources/META-INF/plugin.xml` aligned when changing the version.
- Describe the user-visible result, notable implementation details, linked issue (if any), and verification performed in every pull request.
- Keep each pull request and commit focused; include the tests that validate an implementation change whenever practical.

## Commits

Follow `.agents/skills/commits/SKILL.md`. Use Conventional Commits with an imperative subject, for example `feat(scanner): support gateway routes`, `fix(client): preserve headers`, or `docs: update README`.

Stage only files belonging to the same logical change. Never use `git add .`; inspect the staged diff and confirm the commit message describes it before committing.

## Security and Sensitive Data

- Never commit bearer tokens, passwords, API keys, cookies, local service URLs, or captured production responses.
- Use sanitized fixtures and examples. Avoid logging credentials, request authorization headers, or sensitive payloads.
- Treat persisted workspace data and insecure TLS handling as security-sensitive code. Preserve explicit user consent and add tests when modifying their behavior.
