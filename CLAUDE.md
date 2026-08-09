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

Everything runs on GitHub Actions, in `.github/workflows/`:

| Workflow | Trigger | Does |
|---|---|---|
| `build.yml` | push, PR | build, test, verify, and draft a release on non-PR runs |
| `release.yml` | GitHub `release` event | sign and publish to JetBrains Marketplace, then open a changelog PR |
| `run-ui-tests.yml` | `workflow_dispatch` | UI tests |
| `qodana_code_quality.yml` | push, PR | Qodana inspection |

CI lived on Buildkite between 2026-08-08 and 2026-08-09 (PR #48, reverted).
Worth knowing if you are tempted to move it again: the free plan's hosted
agents are 2 vCPU / 4 GB, and several tasks here need far more. Qodana does a
full Gradle project import, which makes the IntelliJ Platform Gradle Plugin
unpack the whole Android Studio distribution next to Qodana's own analyzer JVM;
`publishPlugin` and `buildPlugin` pull in `buildSearchableOptions`, which starts
an IDE to generate the marketplace search index. Fitting these into 4 GB meant
skipping `buildSearchableOptions`, which ships releases without their search
index. GitHub's runners have the memory free on a public repo.

One pinned version looks wrong and is not:

- `qodana-action@v2026.2` drives `linter: jetbrains/qodana-jvm-community:2025.3`.
  The mismatch is intentional. Older actions fail Gradle import with
  `Invalid Gradle JDK configuration found`; bumping the linter to match the
  action also fails. This pairing is the one that works.

`.idea/gradle.xml` used to pin `gradleJvm` to `jbr-17` while the project
targeted 21. That pin was never the cause of the error above — Qodana passed
with it either way — so it now tracks the toolchain at `jbr-21`.

## Dependencies & Compatibility
- Kotlin 2.4.10, JDK 21
- IntelliJ Platform Gradle Plugin 2.18.1
- Target: Android Studio Quail 3 (2026.1.3.7) and later, `pluginSinceBuild = 261`
- Bundled plugins used: `com.intellij.gradle`, `org.jetbrains.android`, `com.intellij.java`
- No external runtime dependencies

## Commit Conventions
Follow Conventional Commits: `feat:`, `fix:`, `docs:`, `chore:`, `refactor:`, `test:`, `ci:`
