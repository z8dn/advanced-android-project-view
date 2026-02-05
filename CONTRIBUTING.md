# Contributing to Advanced Android Project Tree

Thank you for your interest in contributing to Advanced Android Project Tree! We welcome contributions from the community and are grateful for your support.

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [Getting Started](#getting-started)
- [How to Contribute](#how-to-contribute)
- [Reporting Issues](#reporting-issues)
- [Submitting Pull Requests](#submitting-pull-requests)
- [Development Guidelines](#development-guidelines)
- [Testing](#testing)
- [Commit Message Guidelines](#commit-message-guidelines)

## Code of Conduct

This project adheres to a code of conduct that all contributors are expected to follow. Please be respectful and constructive in your interactions with other contributors.

## Getting Started

### Prerequisites

- **JDK 21 or later**: Required for building the plugin
- **Gradle 8.14.3+**: Included via Gradle Wrapper
- **Android Studio Otter 3 or later**: For development and testing
- **Git**: For version control

### Setting Up Your Development Environment

1. **Fork the repository**
   ```bash
   # Click the "Fork" button on GitHub
   ```

2. **Clone your fork**
   ```bash
   git clone https://github.com/YOUR_USERNAME/advanced-android-project-view.git
   cd advanced-android-project-view
   ```

3. **Add upstream remote**
   ```bash
   git remote add upstream https://github.com/z8dn/advanced-android-project-view.git
   ```

4. **Open in Android Studio**
   - Open Android Studio
   - Select **File** → **Open**
   - Navigate to the cloned directory

5. **Build the project**
   ```bash
   ./gradlew build
   ```

6. **Run the plugin in a development instance**
   ```bash
   ./gradlew runIde
   ```

## How to Contribute

Contributions can take many forms:

- 🐛 **Bug fixes**: Fix issues reported in the issue tracker
- ✨ **New features**: Implement new functionality
- 📝 **Documentation**: Improve README, code comments, or wiki pages
- 🧪 **Tests**: Add test coverage for existing features
- 🎨 **Code quality**: Refactoring, performance improvements
- 🌍 **Translations**: Add or improve i18n support

## Reporting Issues

Before creating a new issue, please:

1. **Search existing issues** to avoid duplicates
2. **Verify the issue** on the latest version of the plugin
3. **Check compatibility** with your Android Studio version

### Creating a Good Bug Report

A good bug report should include:

- **Clear title**: Brief description of the issue
- **Environment details**:
  - Android Studio version
  - Plugin version
  - Operating system
  - JDK version
- **Steps to reproduce**: Numbered list of actions
- **Expected behavior**: What should happen
- **Actual behavior**: What actually happens
- **Screenshots**: If applicable
- **Logs**: Relevant error messages from `idea.log`

**Example:**

```markdown
## Bug: Build directory toggle doesn't persist after restart

**Environment:**
- Android Studio: Otter 3
- Plugin version: 1.0.0
- OS: macOS 14.2
- JDK: 21.0.1

**Steps to reproduce:**
1. Open Android Project View
2. Enable "Display Build Directory"
3. Restart Android Studio
4. Check if setting is still enabled

**Expected:** Setting should persist
**Actual:** Setting reverts to disabled

**Logs:**
```
[error log excerpt]
```
```

### Feature Requests

For feature requests, please include:

- **Use case**: Why is this feature needed?
- **Proposed solution**: How should it work?
- **Alternatives considered**: Other approaches you've thought about
- **Additional context**: Mockups, examples from other plugins, etc.

## Submitting Pull Requests

### Before You Start

1. **Check existing PRs**: Ensure someone isn't already working on the same thing
2. **Create an issue first**: For significant changes, discuss the approach before implementing
3. **Keep changes focused**: One PR should address one issue or feature

### Pull Request Process

1. **Create a feature branch**
   ```bash
   git checkout -b feature/your-feature-name
   # or
   git checkout -b fix/issue-description
   ```

2. **Make your changes**
   - Write clean, documented code
   - Follow existing code style
   - Add tests for new functionality
   - Update documentation as needed

3. **Test your changes**
   ```bash
   # Run tests
   ./gradlew check

   # Test in IDE
   ./gradlew runIde
   ```

4. **Commit your changes**
   ```bash
   git add .
   git commit -m "feat: add support for custom build directories"
   ```
   See [Commit Message Guidelines](#commit-message-guidelines) below.

5. **Keep your fork updated**
   ```bash
   git fetch upstream
   git rebase upstream/main
   ```

6. **Push to your fork**
   ```bash
   git push origin feature/your-feature-name
   ```

7. **Open a Pull Request**
   - Go to your fork on GitHub
   - Click "New Pull Request"
   - Fill out the PR template
   - Link related issues using keywords (e.g., "Closes #123")

### Pull Request Checklist

- [ ] Code follows the project's coding conventions
- [ ] All tests pass (`./gradlew check`)
- [ ] New functionality includes tests
- [ ] Documentation has been updated
- [ ] Commit messages follow conventional commits format
- [ ] No unrelated changes included
- [ ] PR description clearly explains the changes

## Development Guidelines

### Code Style

- **Kotlin**: Follow [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
- **Indentation**: 4 spaces (no tabs)
- **Line length**: Maximum 120 characters
- **Imports**: Remove unused imports, organize alphabetically
- **Naming**:
  - Classes: `PascalCase`
  - Functions/Variables: `camelCase`
  - Constants: `SCREAMING_SNAKE_CASE`

### Documentation

- **Comments**: Explain "why", not "what"
- **README**: Update if adding user-facing features

### Architecture Principles

- **Single Responsibility**: Each class should have one clear purpose
- **DRY (Don't Repeat Yourself)**: Extract common logic into utilities
- **Null Safety**: Leverage Kotlin's null safety features
- **Immutability**: Prefer immutable data structures
- **Module Disposal**: Always check `module.isDisposed` before accessing module data

### Performance Considerations

- **Lazy Loading**: Don't load data until it's needed
- **Caching**: Cache filesystem lookups when appropriate
- **Background Threads**: Keep UI thread responsive
- **Resource Cleanup**: Properly dispose of resources

## Testing

### Running Tests

```bash
# Run all tests
./gradlew test

# Run all checks (tests + inspections)
./gradlew check
```

### Writing Tests

- **Unit tests**: Test individual functions in isolation
- **Integration tests**: Test component interactions
- **Test naming**: Use descriptive names that explain what is being tested

**Example Test:**

```kotlin
class AndroidViewNodeUtilsTest {
    @Test
    fun `findBuildDirectory should return null for disposed module`() {
        val module = mock<Module> {
            on { isDisposed } doReturn true
        }

        val result = AndroidViewNodeUtils.findBuildDirectory(module)

        assertNull(result)
    }
}
```

## Commit Message Guidelines

This project follows [Conventional Commits](https://www.conventionalcommits.org/) specification.

### Format

```text
<type>(<scope>): <subject>

<body>

<footer>
```

### Types

- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation changes
- `style`: Code style changes (formatting, no logic change)
- `refactor`: Code refactoring
- `perf`: Performance improvements
- `test`: Adding or updating tests
- `chore`: Build process, dependencies, tooling

### Examples

```bash
# Feature
feat(readme): add support for multiple README file formats

# Bug fix
fix(nodes): prevent duplicate nodes for non-Android modules

# Documentation
docs(contributing): add commit message guidelines

# Refactor
refactor(utils): extract common filesystem operations

# Breaking change
feat(settings)!: change settings storage format

BREAKING CHANGE: Settings from v1.x will not be migrated automatically
```

### Scope (Optional)

Scopes help identify which part of the codebase is affected:
- `nodes`: Node provider logic
- `settings`: Settings and persistence
- `actions`: User actions/commands
- `utils`: Utility functions
- `readme`: README-related features
- `build`: Build directory features

### Subject

- Use imperative mood: "add" not "added" or "adds"
- Don't capitalize first letter
- No period at the end
- Maximum 72 characters

### Body (Optional)

- Explain what and why, not how
- Wrap at 72 characters
- Separate from subject with blank line

### Footer (Optional)

- Reference issues: `Closes #123`, `Fixes #456`
- Note breaking changes: `BREAKING CHANGE: description`

## Questions?

If you have questions about contributing:

- 💬 Open a [discussion](https://github.com/z8dn/advanced-android-project-view/discussions)
- 🐛 Create an [issue](https://github.com/z8dn/advanced-android-project-view/issues)
- 📧 Contact the maintainers

---

Thank you for contributing to Advanced Android Project Tree! 🎉
