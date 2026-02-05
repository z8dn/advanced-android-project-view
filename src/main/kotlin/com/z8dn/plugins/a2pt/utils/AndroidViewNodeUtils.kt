package com.z8dn.plugins.a2pt.utils

import com.z8dn.plugins.a2pt.settings.AndroidViewSettings

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.FileSystems
import java.nio.file.PathMatcher

/**
 * Utility functions for finding files and directories in Android Project View nodes.
 *
 * This object provides shared helper methods used by multiple node providers
 * to avoid code duplication and ensure consistent behavior.
 */
object AndroidViewNodeUtils {

    private const val BUILD_DIRECTORY_NAME = "build"

    /**
     * Checks if a filename matches any of the specified patterns.
     * Supports glob patterns (e.g., "*.md") and exact matches (case-insensitive).
     *
     * @param filename The filename to check
     * @param patterns List of patterns to match against
     * @return true if the filename matches any pattern
     */
    private fun matchesAnyPattern(filename: String, patterns: List<String>): Boolean {
        val fileSystem = FileSystems.getDefault()
        val filenameLower = filename.lowercase()

        for (pattern in patterns) {
            val patternLower = pattern.lowercase()

            // Try glob pattern matching (case-insensitive)
            try {
                val matcher: PathMatcher = fileSystem.getPathMatcher("glob:$patternLower")
                val path = fileSystem.getPath(filenameLower)
                if (matcher.matches(path)) {
                    return true
                }
            } catch (_: Exception) {
                // If glob pattern fails, continue to next pattern
            }

            // Also check case-insensitive exact match
            if (filenameLower == patternLower) {
                return true
            }
        }

        return false
    }

    /**
     * Finds the build directory in the module's content roots.
     *
     * @param module The module to search in
     * @return The build directory VirtualFile, or null if not found or module is disposed
     */
    fun findBuildDirectory(module: Module): VirtualFile? {
        if (module.isDisposed) return null

        val contentRoots = ModuleRootManager.getInstance(module).contentRoots

        for (root in contentRoots) {
            val buildDir = root.findChild(BUILD_DIRECTORY_NAME)
            if (buildDir != null && buildDir.isDirectory && buildDir.isValid) {
                return buildDir
            }
        }
        return null
    }

    /**
     * Checks if project files should be shown inside modules rather than in a top-level group.
     *
     * @return true if project files should be shown in modules, false for top-level group
     */
    fun showProjectFilesInModule(): Boolean {
        return AndroidViewSettings.getInstance().showProjectFilesInModule
    }

    /**
     * Gets all project files from the entire project that match configured patterns.
     * Searches all module content roots in the project.
     * This is analogous to getting all build files from the project system.
     *
     * @param project The project to search in
     * @return List of all matching VirtualFiles across all modules
     */
    fun getAllProjectFilesInProject(project: com.intellij.openapi.project.Project): List<VirtualFile> {
        val settings = AndroidViewSettings.getInstance()
        val allPatterns = settings.projectFileGroups.flatMap { it.patterns }
        if (allPatterns.isEmpty()) return emptyList()

        val result = mutableListOf<VirtualFile>()
        val moduleManager = com.intellij.openapi.module.ModuleManager.getInstance(project)

        for (module in moduleManager.modules) {
            if (module.isDisposed) continue

            val contentRoots = ModuleRootManager.getInstance(module).contentRoots
            for (root in contentRoots) {
                for (child in root.children) {
                    if (child.isValid && !child.isDirectory && matchesAnyPattern(child.name, allPatterns)) {
                        result.add(child)
                    }
                }
            }
        }

        return result
    }
}
