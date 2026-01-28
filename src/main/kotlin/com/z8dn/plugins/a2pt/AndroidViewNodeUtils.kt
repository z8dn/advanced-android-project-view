package com.z8dn.plugins.a2pt

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
     * Finds files matching the specified patterns in the module's content roots.
     * Searches only immediate children of each content root.
     *
     * @param module The module to search in
     * @param patterns List of file patterns (e.g., "*.md", "LICENSE", "CHANGELOG.md")
     * @return List of matching VirtualFiles, or empty list if none found or module is disposed
     */
    fun findMatchingFiles(module: Module, patterns: List<String>): List<VirtualFile> {
        if (module.isDisposed || patterns.isEmpty()) return emptyList()

        val contentRoots = ModuleRootManager.getInstance(module).contentRoots
        val matchingFiles = mutableListOf<VirtualFile>()

        for (root in contentRoots) {
            for (child in root.children) {
                if (child.isValid && !child.isDirectory && matchesAnyPattern(child.name, patterns)) {
                    matchingFiles.add(child)
                }
            }
        }

        return matchingFiles
    }

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
            } catch (e: Exception) {
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
     * Checks if project files should be shown inside modules rather than in a top-level group.
     *
     * @return true if project files should be shown in modules, false for top-level group
     */
    fun showProjectFilesInModule(): Boolean {
        return AndroidViewSettings.getInstance().showProjectFilesInModule
    }

    /**
     * Finds all project files matching the group's patterns in the module's content roots.
     *
     * @param module The module to search in
     * @param group The project file group with patterns to match
     * @return List of matching VirtualFiles
     */
    fun findProjectFilesForGroup(module: Module, group: ProjectFileGroup): List<VirtualFile> {
        return findMatchingFiles(module, group.patterns)
    }

    /**
     * Finds all project files grouped by their group configuration.
     *
     * @param module The module to search in
     * @return Map of group to list of matching files
     */
    fun findAllProjectFilesByGroup(module: Module): Map<ProjectFileGroup, List<VirtualFile>> {
        val settings = AndroidViewSettings.getInstance()
        val result = mutableMapOf<ProjectFileGroup, List<VirtualFile>>()

        for (group in settings.projectFileGroups) {
            val files = findProjectFilesForGroup(module, group)
            if (files.isNotEmpty()) {
                result[group] = files
            }
        }

        return result
    }
}
