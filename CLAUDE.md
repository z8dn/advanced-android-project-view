# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Advanced Android Project Tree (A2PT)** is an IntelliJ Platform plugin for Android Studio that enhances the Android Project View with customizable file grouping and build directory visibility.

## Common Commands

```bash
./gradlew build              # Full build with all checks
./gradlew buildPlugin        # Build the plugin distribution ZIP
./gradlew runIde             # Run plugin in a development Android Studio instance
./gradlew test               # Run unit tests
./gradlew check              # Run all tests and code inspections
./gradlew verifyPlugin       # Run plugin structure verification
./gradlew runPluginVerifier  # Run plugin verification on targeted IDEs
```

## Architecture

The plugin intercepts IntelliJ's project tree via extension points and injects custom nodes.

**Package:** `com.z8dn.plugins.a2pt`

### Key Extension Points (registered in `plugin.xml`)
- `treeStructureProvider` — intercepts and modifies the Android Project View tree
- `androidViewNodeProvider` — extends Android module nodes with custom children
- `applicationService` — `AndroidViewSettings` for persistent state
- `applicationConfigurable` — settings UI under Tools menu
- `postStartupActivity` — refreshes view on project open
- `searchScopesProvider` — exposes file groups as Find/Replace scopes

### Source Layout
- `actions/` — toggle actions for build dir and module-level file display
- `nodes/` — custom tree node classes (`ProjectFileGroupNode`, `ProjectFileNode`, `GradleModuleWithProjectFiles`)
- `providers/` — tree structure providers that inject nodes (`AdvancedAndroidViewProvider`, `CustomNonAndroidNodeProvider`, `ProjectFilesTreeStructureProvider`)
- `settings/` — `AndroidViewSettings` (PersistentStateComponent), configurable UI, and group dialog
- `scopes/` — `ProjectFileGroupScopeProvider` for search scope integration
- `utils/` — file matching (`AndroidViewNodeUtils`) and display formatting (`ProjectFileDisplayUtils`)

### Data Flow
1. User toggles features via actions → stored in `AndroidViewSettings` (persisted as `androidViewSettings.xml`)
2. Tree providers read settings and intercept the tree to inject custom nodes
3. File matching uses glob patterns and case-insensitive matching via `PatternMatcher`
4. Two display modes: files under each module (`showProjectFilesInModule=true`) or grouped at project root

### Key Settings
- `showBuildDirectory` — show/hide `build/` directories in the tree
- `showProjectFilesInModule` — toggle between module-level and project-root grouping
- `projectFileGroups` — list of `ProjectFileGroup` objects (name + glob patterns)

## CI

CI is a deliberate hybrid, not a single system:

- **Buildkite** runs build, test, verify, release draft, publish and UI tests, as
  one pipeline defined in `.buildkite/pipeline.yml`. See `.buildkite/CLAUDE.md`
  before changing anything there — the memory constraints and step conditions
  are not obvious from the YAML alone.
- **GitHub Actions** runs Qodana only (`.github/workflows/qodana_code_quality.yml`).
  It stays there because Qodana performs a full Gradle project import, which
  makes the IntelliJ Platform Gradle Plugin unpack the whole Android Studio
  distribution next to Qodana's own analyzer JVM. That needs far more memory
  than the Buildkite agents have, and GitHub's runners provide it free.

Two pinned versions that look wrong and are not:

- `qodana-action@v2026.2` drives `linter: jetbrains/qodana-jvm-community:2025.3`.
  The mismatch is intentional. Older actions fail Gradle import with
  `Invalid Gradle JDK configuration found`; bumping the linter to match the
  action also fails. This pairing is the one that works.
- `.idea/gradle.xml` pins `gradleJvm` to `jbr-17` while the project targets 21.
  This looks like the cause of the error above and is not — changing it does not
  help, and Qodana passes with it as-is.

CI-only Gradle settings belong in `gradle/ci.properties`, never in
`gradle.properties`, so local builds keep their own tuning.

## Dependencies & Compatibility
- Kotlin 2.3.0, JDK 21
- IntelliJ Platform Gradle Plugin 2.14.0
- Target: Android Studio Otter 3 Feature Drop (2025.2.3.9) and later
- Bundled plugins used: `com.intellij.gradle`, `org.jetbrains.android`, `com.intellij.java`
- No external runtime dependencies

## Commit Conventions
Follow Conventional Commits: `feat:`, `fix:`, `docs:`, `chore:`, `refactor:`, `test:`, `ci:`
