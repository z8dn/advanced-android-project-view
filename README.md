# Advanced Android Project View (A2PT)

[![Build](https://github.com/z8dn/advanced-android-project-view/workflows/Build/badge.svg)](https://github.com/z8dn/advanced-android-project-view/actions)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

<!-- Plugin description -->
**Advanced Android Project View** enhances the Android Studio project view by providing quick access to commonly used directories and files that are normally hidden or difficult to navigate to. This plugin helps Android developers streamline their workflow by making build directories and README files readily accessible in the project tree.
<!-- Plugin description end -->

## Features

### 🗂️ Build Directory Visibility
Toggle visibility of `build` directories for all modules directly in the Android Project View. No more navigating through the file system to inspect build outputs, APKs, or generated files.

### 📄 README File Display
Instantly access README files from any module in your project tree. Perfect for documenting module-specific information and keeping documentation close to the code.

### 🎯 Module-Level Control
Works seamlessly with both Android and non-Android Gradle modules, automatically adapting to your project structure.

### ⚡ Performance Optimized
- Minimal overhead with lazy loading
- Efficient filesystem lookups with caching
- No impact on IDE startup time

## Installation

### From JetBrains Marketplace (Recommended)
1. Open Android Studio
2. Go to **Settings/Preferences** → **Plugins** → **Marketplace**
3. Search for "Advanced Android Project View"
4. Click **Install** and restart Android Studio

### Manual Installation
1. Download the [latest release](https://github.com/z8dn/advanced-android-project-view/releases/latest)
2. Open Android Studio
3. Go to **Settings/Preferences** → **Plugins** → ⚙️ → **Install Plugin from Disk...**
4. Select the downloaded ZIP file
5. Restart Android Studio

## Usage

### Toggle Build Directory Visibility
1. Open the Android Project View
2. Click the **Android** dropdown at the top of the project pane
3. Look for "Display Build Directory" in the appearance actions menu
4. Toggle the option on/off

Alternatively, right-click in the Project View toolbar and select appearance options.

### Toggle README Visibility
1. Open the Android Project View
2. Click the **Android** dropdown at the top of the project pane
3. Look for "Display README Files" in the appearance actions menu
4. Toggle the option on/off

Your preferences are saved automatically and will persist across IDE restarts.

## Compatibility

- **Android Studio**: Otter 2 Feature Drop (2025.2.2) and later
- **IntelliJ IDEA Ultimate**: 2025.2+ with Android plugin
- **Platform**: All (Windows, macOS, Linux)

## Building from Source

### Prerequisites
- JDK 21 or later
- Gradle 8.14.3 or later (included via wrapper)
- Android Studio Otter 3 or later (for local development)

### Build Steps

```bash
# Clone the repository
git clone https://github.com/z8dn/advanced-android-project-view.git
cd advanced-android-project-view

# Build the plugin
./gradlew buildPlugin

# The plugin ZIP will be created in build/distributions/
```

### Running in Development Mode

```bash
# Launch Android Studio with the plugin installed
./gradlew runIde
```

### Running Tests

```bash
# Run all tests
./gradlew check

# Run tests with coverage
./gradlew check koverHtmlReport
```

## Contributing

We welcome contributions from the community! Whether you want to fix a bug, add a feature, or improve documentation, your help is appreciated.

Please read our [Contributing Guidelines](./CONTRIBUTING.md) for detailed information on:

- 🐛 Reporting issues
- ✨ Submitting pull requests
- 📝 Code style and conventions
- 🧪 Testing requirements
- 💬 Commit message format

For quick reference:
- Use [Conventional Commits](https://www.conventionalcommits.org/) format
- Ensure all tests pass with `./gradlew check`
- Follow Kotlin coding conventions
- Add tests for new features

## Architecture

The plugin uses the IntelliJ Platform's extension point system:

```text
com.z8dn.plugins.a2pt/
├── AndroidViewSettings          # Persistent settings storage
├── AndroidViewCustomNodesProvider  # Main provider for custom nodes
├── AndroidViewNodeUtils         # Shared utility methods
├── NonAndroidModuleWithCustomNodes # Wrapper for non-Android modules
└── Actions/
    ├── ShowBuildDirectoryAction # Toggle for build directories
    └── ShowReadmeAction        # Toggle for README files
```

### Key Design Decisions

- **Single Provider Pattern**: Uses one consolidated provider to avoid duplicate node creation
- **Lazy Evaluation**: Files are discovered only when needed
- **Precomputed Results**: Filesystem lookups are cached to prevent redundant operations
- **Module Disposal Checks**: Gracefully handles module lifecycle events

## License

```text
Copyright (c) 2026 z8dn

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

## Acknowledgments

- Built with [IntelliJ Platform Plugin Template](https://github.com/JetBrains/intellij-platform-plugin-template)
- Inspired by the need for better Android project navigation
- Thanks to all [contributors](https://github.com/z8dn/advanced-android-project-view/graphs/contributors)

## Support

- 🐛 [Issue Tracker](https://github.com/z8dn/advanced-android-project-view/issues)
- 💬 [Discussions](https://github.com/z8dn/advanced-android-project-view/discussions)

---

**Made with ❤️ for the Android developer community**
