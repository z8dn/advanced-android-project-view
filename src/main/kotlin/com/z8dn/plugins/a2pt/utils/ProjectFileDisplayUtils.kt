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

    /**
     * Checks if a filename matches a pattern (glob or exact match).
     * Matching is case-insensitive.
     *
     * @param filename The filename to check
     * @param pattern The pattern to match against (supports glob patterns like *.md)
     * @return true if the filename matches the pattern
     */
    fun matchesPattern(filename: String, pattern: String): Boolean {
        val filenameLower = filename.lowercase()
        val patternLower = pattern.lowercase()

        // Try glob pattern matching
        try {
            val matcher = java.nio.file.FileSystems.getDefault()
                .getPathMatcher("glob:$patternLower")
            val path = java.nio.file.FileSystems.getDefault().getPath(filenameLower)
            if (matcher.matches(path)) {
                return true
            }
        } catch (_: Exception) {
            // Fall through to exact match
        }

        // Exact match
        return filenameLower == patternLower
    }
}
