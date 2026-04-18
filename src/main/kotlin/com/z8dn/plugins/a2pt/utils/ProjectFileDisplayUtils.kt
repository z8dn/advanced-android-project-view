package com.z8dn.plugins.a2pt.utils

import com.android.tools.idea.projectsystem.gradle.getGradleIdentityPath
import com.android.tools.idea.projectsystem.gradle.getGradleProjectPath
import com.android.tools.idea.projectsystem.gradle.toHolder
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile

/**
 * Utility object for generating display names and matching patterns for project files.
 * Provides shared functionality across different providers and nodes.
 */
object ProjectFileDisplayUtils {

    private const val MODULE_PREFIX = "Module "
    private const val PROJECT_PREFIX = "Project: "
    private const val BUILD_PREFIX = "Included build: "

    /**
     * Generates a display name for a file based on its type and module.
     * Returns the module identifier with appropriate prefix.
     *
     * @param file The VirtualFile to generate a display name for
     * @param module The module containing the file
     * @return Display name with module prefix (Module/Project/Build)
     */
    fun generateDisplayName(file: VirtualFile, module: Module): String {
        // Get Gradle identity path and project path to determine the display name format
        val gradleIdentityPath = module.getGradleIdentityPath()
        val gradleProjectPath = module.getGradleProjectPath()?.toHolder()

        // Determine the project display name with appropriate prefix
        val projectDisplayName = when {
            gradleIdentityPath == ":" -> PROJECT_PREFIX + module.name
            gradleProjectPath?.path == ":" -> BUILD_PREFIX + gradleIdentityPath
            else -> MODULE_PREFIX + (gradleIdentityPath ?: module.name)
        }

        // When files are shown in top-level group, always show the full prefix
        // to identify which module they belong to
        return projectDisplayName
    }

    private fun globMatches(input: String, pattern: String): Boolean {
        val inputLower = input.lowercase().replace('\\', '/')
        val patternLower = pattern.lowercase()
        return try {
            val fs = java.nio.file.FileSystems.getDefault()
            fs.getPathMatcher("glob:$patternLower").matches(fs.getPath(inputLower))
        } catch (_: Exception) {
            inputLower == patternLower
        }
    }

    fun matchesPattern(filename: String, pattern: String): Boolean {
        if (pattern.startsWith("!")) return false
        return globMatches(filename, pattern)
    }

    /**
     * Returns true if [filename]/[relativePath] should be included by [patterns].
     * Gitignore semantics: included if matches any inclusion pattern and no exclusion pattern.
     * Exclusion patterns are prefixed with `!` (e.g. `!archive/*.md`).
     * Path patterns (e.g. `docs/*.txt`) are matched against [relativePath].
     */
    fun matchesPatterns(filename: String, relativePath: String, patterns: List<String>): Boolean {
        var included = false
        for (pattern in patterns) {
            if (pattern.startsWith("!")) {
                val p = pattern.removePrefix("!")
                if (globMatches(filename, p) || globMatches(relativePath, p)) return false
            } else {
                if (!included && (globMatches(filename, pattern) || globMatches(relativePath, pattern))) {
                    included = true
                }
            }
        }
        return included
    }
}
