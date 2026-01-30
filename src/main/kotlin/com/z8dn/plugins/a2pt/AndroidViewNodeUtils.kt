package com.z8dn.plugins.a2pt

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
     * Checks if project files should be shown inside modules rather than in a top-level group.
     *
     * @return true if project files should be shown in modules, false for top-level group
     */
    fun showProjectFilesInModule(): Boolean {
        return AndroidViewSettings.getInstance().showProjectFilesInModule
    }

    /**
     * Checks if a PsiFile should be shown in the project-level files group.
     * Similar to showInProjectBuildScriptsGroup for build files.
     *
     * @return true if file should be shown in project-level group, false if in module
     */
    fun showInProjectFilesGroup(): Boolean {
        // If showProjectFilesInModule is false, all project files go to project-level group
        return !showProjectFilesInModule()
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
