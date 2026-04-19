package com.z8dn.plugins.a2pt.utils

import com.z8dn.plugins.a2pt.settings.AndroidViewSettings

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VfsUtil
import java.nio.file.FileSystems
import java.nio.file.PathMatcher
import java.nio.file.Paths

/**
 * Utility functions for finding files and directories in Android Project View nodes.
 *
 * This object provides shared helper methods used by multiple node providers
 * to avoid code duplication and ensure consistent behavior.
 */
object AndroidViewNodeUtils {

    private const val BUILD_DIRECTORY_NAME = "build"

    /**
     * Checks whether a file should be included based on a list of patterns.
     * Patterns prefixed with ! are exclusions. Patterns containing / are matched
     * against the relative path; others match the filename only.
     * A file is included when it matches at least one inclusion pattern AND
     * does not match any exclusion pattern.
     *
     * @param fileName     The bare filename
     * @param relativePath The file path relative to the project root
     * @param patterns     List of patterns (may include !-prefixed exclusions)
     * @return true if the file should be included
     */
    fun matchesPatterns(fileName: String, relativePath: String, patterns: List<String>): Boolean {
        val exclusions = patterns.filter { it.startsWith("!") }.map { it.removePrefix("!") }
        val inclusions = patterns.filter { !it.startsWith("!") }

        val included = inclusions.isEmpty() || inclusions.any { matchSingle(it, fileName, relativePath) }
        val excluded = exclusions.any { matchSingle(it, fileName, relativePath) }
        return included && !excluded
    }

    /**
     * Matches a single pattern against a file. Patterns containing `/` are matched
     * against [relativePath]; all others are matched against [fileName].
     */
    private fun matchSingle(pattern: String, fileName: String, relativePath: String): Boolean {
        val patternLower = pattern.lowercase()
        return if ('/' in patternLower) {
            matchGlob(patternLower, relativePath.lowercase()) ||
                patternLower == relativePath.lowercase()
        } else {
            matchGlob(patternLower, fileName.lowercase()) ||
                patternLower == fileName.lowercase()
        }
    }

    private fun matchGlob(pattern: String, target: String): Boolean {
        return try {
            val matcher: PathMatcher = FileSystems.getDefault().getPathMatcher("glob:$pattern")
            matcher.matches(Paths.get(target))
        } catch (_: Exception) {
            false
        }
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
     * Searches all module content roots, and also directly navigates to directories
     * indicated by path-based patterns (to support non-Gradle-module folders).
     *
     * @param project The project to search in
     * @return List of (VirtualFile, relativePath) pairs for all matching files
     */
    fun getAllProjectFilesInProject(project: com.intellij.openapi.project.Project): List<Pair<VirtualFile, String>> {
        val settings = AndroidViewSettings.getInstance()
        val allPatterns = settings.projectFileGroups.flatMap { it.patterns }
        if (allPatterns.isEmpty()) return emptyList()

        val projectBaseDir = project.basePath
            ?.let { LocalFileSystem.getInstance().findFileByPath(it) }
            ?: return emptyList()
        val result = mutableListOf<Pair<VirtualFile, String>>()
        val moduleManager = com.intellij.openapi.module.ModuleManager.getInstance(project)

        for (module in moduleManager.modules) {
            if (module.isDisposed) continue

            val contentRoots = ModuleRootManager.getInstance(module).contentRoots
            for (root in contentRoots) {
                for (child in root.children) {
                    if (child.isValid && !child.isDirectory) {
                        val relativePath = VfsUtil.getRelativePath(child, projectBaseDir, '/') ?: child.name
                        if (matchesPatterns(child.name, relativePath, allPatterns)) {
                            result.add(child to relativePath)
                        }
                    }
                }
            }
        }

        // Also scan directories pointed to directly by path-based patterns.
        // This handles non-Gradle-module folders (e.g. a top-level "docs/" folder).
        val seenPaths = result.map { it.first.path }.toMutableSet()
        for (entry in getFilesFromPathBasedPatterns(projectBaseDir, allPatterns)) {
            if (seenPaths.add(entry.first.path)) {
                result.add(entry)
            }
        }

        return result
    }

    /**
     * For each path-based inclusion pattern (containing '/'), extracts the non-wildcard
     * directory prefix and navigates there directly from [projectBaseDir], collecting all
     * files that match the full pattern list. This is decoupled from the module scan.
     *
     * e.g. "docs/guide.md" or "docs/glob-pattern" → scans <project>/docs/ directly
     *      "docs/sub/glob-pattern"                 → scans <project>/docs/sub/ directly
     *      "docs/glob-recursive"                   → scans <project>/docs/ recursively
     */
    private fun getFilesFromPathBasedPatterns(
        projectBaseDir: VirtualFile,
        patterns: List<String>
    ): List<Pair<VirtualFile, String>> {
        val result = mutableListOf<Pair<VirtualFile, String>>()
        val dirPrefixes = patterns
            .filter { !it.startsWith("!") }
            .mapNotNull { extractDirectoryPrefix(it) }
            .toSet()
        for (prefix in dirPrefixes) {
            val targetDir = projectBaseDir.findFileByRelativePath(prefix) ?: continue
            if (!targetDir.isValid || !targetDir.isDirectory) continue
            scanDirectoryForFiles(targetDir, projectBaseDir, patterns, result)
        }
        return result
    }

    /**
     * Extracts the non-wildcard directory prefix from a path-based pattern.
     * Returns null for filename-only patterns (no '/') or patterns whose first
     * directory segment contains a wildcard.
     *
     * e.g. "docs/guide.md"     → "docs"
     *      "docs/sub/guide.md" → "docs/sub"
     *      "docs/deep/glob"    → "docs"
     *      "filename-only"     → null
     *      "wildcard-dir/file" → null (when dir segment contains glob chars)
     */
    private fun extractDirectoryPrefix(pattern: String): String? {
        if ('/' !in pattern) return null
        val segments = pattern.split('/')
        val dirSegments = segments.dropLast(1)
        val prefix = dirSegments.takeWhile { '*' !in it && '?' !in it }
        return if (prefix.isEmpty()) null else prefix.joinToString("/")
    }

    private fun scanDirectoryForFiles(
        dir: VirtualFile,
        projectBaseDir: VirtualFile,
        patterns: List<String>,
        result: MutableList<Pair<VirtualFile, String>>
    ) {
        for (child in dir.children) {
            if (!child.isValid) continue
            if (child.isDirectory) {
                scanDirectoryForFiles(child, projectBaseDir, patterns, result)
            } else {
                val relativePath = VfsUtil.getRelativePath(child, projectBaseDir, '/') ?: child.name
                if (matchesPatterns(child.name, relativePath, patterns)) {
                    result.add(child to relativePath)
                }
            }
        }
    }
}
