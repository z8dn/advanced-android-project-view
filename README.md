<h1 align="center">Advanced Android Project View (A2PT)</h1></br>

<p align="center">
  <a href="https://opensource.org/licenses/Apache-2.0"><img alt="License" src="https://img.shields.io/badge/License-Apache%202.0-blue.svg"/></a>
  <a href="https://plugins.jetbrains.com/plugin/29265-revenuecat-dashboard"><img alt="JetBrains Plugin" src="https://img.shields.io/badge/JetBrains-Plugin-orange.svg"/></a>
  <a href="https://github.com/z8dn/advanced-android-project-view/actions"><img alt="Build" src="https://github.com/z8dn/advanced-android-project-view/workflows/Build/badge.svg"/></a>
</p>

<!-- Plugin description -->
<p><strong>Advanced Android Project View</strong> enhances the Android Studio project view by providing quick access to commonly used directories and files that are normally hidden or difficult to navigate to.</p>

<h3>Features</h3>
<ul>
  <li><strong>Quick Access to Build Directories:</strong> Navigate directly to build outputs, generated sources, and intermediate files without switching views or using the file system explorer.</li>
  <li><strong>Customizable Project Groups:</strong> Organize project files with configurable groups in the project tree, tailored to your workflow and development needs.</li>
  <li><strong>Hidden Files Visibility:</strong> Easily access configuration files and directories that are typically hidden from the standard Android view, improving discoverability and reducing context switching.</li>
</ul>
<!-- Plugin description end -->

## 🗺️ Roadmap

We have exciting plans for future updates! Here are some features currently under consideration or in development:

- [x] **IDE Account Sync**: Sync your custom group configurations across different machines using your IDE account
- [ ] **Saved Presets**: Pre-defined file groups for common stacks (KMP, Compose Multiplatform, etc.) ([#30](https://github.com/z8dn/advanced-android-project-view/issues/30))
- [ ] **Dynamic Icons**: Customizable icons for specific file patterns ([#31](https://github.com/z8dn/advanced-android-project-view/issues/31))
- [ ] **Enhanced Filtering**: More advanced pattern matching for file groups ([#32](https://github.com/z8dn/advanced-android-project-view/issues/32))

Have a suggestion? Feel free to open an [issue](https://github.com/z8dn/advanced-android-project-view/issues) or start a [discussion](https://github.com/z8dn/advanced-android-project-view/discussions)!

## Installation

### From JetBrains Marketplace (Recommended)
1. Open Android Studio/IntelliJ IDEA
2. Go to **Settings/Preferences** (⌘, on Mac or Ctrl+Alt+S on Windows/Linux) → **Plugins** → **Marketplace**
3. Search for "Advanced Android Project View"
4. Click **Install** and restart Android Studio/IntelliJ IDEA
![img/plugin-marketplace-installation.png](img/plugin-marketplace-installation.png)

### Manual Installation
1. Download the [latest release](https://github.com/z8dn/advanced-android-project-view/releases/latest)
2. Open Android Studio
3. Go to **Settings/Preferences** (⌘, on Mac or Ctrl+Alt+S on Windows/Linux) → **Plugins** → ⚙️ → **Install Plugin from Disk...**
4. Select the downloaded ZIP file
5. Restart Android Studio

## Usage

### Toggle Build Directory Visibility
1. Open the Android Project View
2. Click the **Android** dropdown at the top of the project pane
3. Look for "Display Build Directory" in the appearance actions menu
4. Toggle the option on/off
![img/build-directory-toggle.png](img/build-directory-toggle.png)
Alternatively, right-click in the Project View toolbar and select appearance options.

### Configure Project File Groups
1. Go to **Settings/Preferences** → **Tools** → **Advanced Android Project View**
2. Click **Add** to create a new file group
3. Enter a group name (e.g., "Documentation", "AI Rules")
   ![img/settings-custom-file-groups.png](img/settings-custom-file-groups.png)
4. Add file patterns using wildcards (e.g., `*.md`) or exact filenames (e.g., `CLAUDE.md`)
![img/edit-file-group-dialog.png](img/edit-file-group-dialog.png)
5. Click **OK** to apply changes

**Pattern Examples:**
- `*.md` - All Markdown files
- `CLAUDE.md` - Claude AI rules file
- `*.yml` - All YAML configuration files

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

## License

```text
Copyright (c) 2026 z8dn

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

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
