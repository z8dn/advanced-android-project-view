package com.z8dn.plugins.a2pt.utils

import com.z8dn.plugins.a2pt.settings.AndroidViewSettings
import com.z8dn.plugins.a2pt.settings.ProjectFileGroup

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VfsUtil
import java.nio.file.FileSystems
import java.nio.file.PathMatcher
import java.nio.file.Paths
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

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
     *
     * Exposed for the file group preview, which needs a per-pattern match count.
     * The pattern must already have any leading `!` stripped by the caller.
     */
    fun matchesSinglePattern(fileName: String, relativePath: String, pattern: String): Boolean =
        matchSingle(pattern, fileName, relativePath)

    /**
     * Checks whether any group actually claims this file, applying each group's full pattern
     * list — exclusions included.
     *
     * [collectMatchingFiles] deliberately drops exclusions, because discovery is shared across
     * groups. Whoever consumes that pool must reapply them, or a `!` pattern has no effect.
     *
     * @param groups the groups to test against, each with its complete pattern list
     */
    fun matchesAnyGroup(
        fileName: String,
        relativePath: String,
        groups: List<ProjectFileGroup>
    ): Boolean = groups.any { matchesPatterns(fileName, relativePath, it.patterns) }

    /**
     * One sweep's worth of discovery: the shared candidate [pool], and the per-module
     * attribution derived from the same walk.
     */
    class ProjectFileSweep(
        val pool: List<Pair<VirtualFile, String>>,
        val byModule: Map<Module, List<VirtualFile>>
    )

    /**
     * Walks the project once, producing both the candidate pool and the per-module attribution.
     *
     * Attribution comes from the walk itself — a file is claimed by the module whose content root
     * it was found directly under — rather than from re-testing every file against every module
     * afterwards. That is what removes the quadratic term: the previous shape swept the whole
     * project once per module and then ran up to `2 x files x modules` containment checks.
     *
     * [discover] examines the *immediate* children of each content root, so a nested content
     * root's files reach the inner module only — exactly what
     * [ModuleUtilCore.moduleContainsFile] reported before. Files found by the path-pattern scan
     * sit outside every content root by definition, so they cost one
     * [ModuleUtilCore.findModuleForFile] each.
     *
     * One deliberate narrowing: the old filter also accepted a file reachable through a module's
     * *library* roots. Project files matched by these patterns (READMEs, gradle scripts, docs) do
     * not live in library roots, and treating a library file as a module's own project file was
     * never intended.
     *
     * Must be called inside a read action: this touches the VFS.
     */
    fun sweep(project: Project, groups: List<ProjectFileGroup>): ProjectFileSweep {
        val inclusionPatterns = groups.flatMap { it.patterns }.filter { !it.startsWith("!") }
        val discovered = discover(project, inclusionPatterns)
        if (discovered.isEmpty()) return ProjectFileSweep(emptyList(), emptyMap())

        val byModule = mutableMapOf<Module, MutableList<VirtualFile>>()
        for (candidate in discovered) {
            if (!matchesAnyGroup(candidate.file.name, candidate.relativePath, groups)) continue

            // A path-pattern hit sits outside every content root by definition, so it is the only
            // case that has to ask which module owns it.
            val owner = candidate.contentRootOwner
                ?: ModuleUtilCore.findModuleForFile(candidate.file, project)
                ?: continue
            byModule.getOrPut(owner) { mutableListOf() }.add(candidate.file)
        }

        return ProjectFileSweep(
            pool = discovered.distinctBy { it.file }.map { it.file to it.relativePath },
            byModule = byModule.mapValues { (_, files) -> files.distinct() }
        )
    }

    /**
     * The project files to show inside [module] when `showProjectFilesInModule` is enabled:
     * everything some group claims that this module also contains.
     *
     * This is the uncached path. The tree goes through
     * [com.z8dn.plugins.a2pt.index.ProjectFileGroupIndex] instead, which keeps one [sweep] rather
     * than running one per module.
     *
     * @return List of project files (VirtualFile) belonging to this module
     */
    fun getProjectFilesForModule(module: Module): List<VirtualFile> {
        val groups = AndroidViewSettings.getInstance().projectFileGroups
        return sweep(module.project, groups).byModule[module] ?: emptyList()
    }

    /**
     * Whether [path] lies under one of the directories the sweep never descends into.
     *
     * Exposed for the index's VFS listener, which must not invalidate a cached sweep because of
     * a change the sweep would never have seen — a Gradle build writing into `build/` being the
     * case that matters.
     */
    fun isInIgnoredDirectory(path: String): Boolean =
        path.splitToSequence('/').any { it in IGNORED_SCAN_DIRECTORIES }

    /**
     * Checks whether [pattern] compiles as a glob. A pattern that does not compile can
     * never match a file, which is otherwise indistinguishable from one that simply
     * matches nothing — the preview reports the two differently.
     *
     * The leading `!` of an exclusion must be stripped by the caller.
     */
    fun isValidGlob(pattern: String): Boolean = compileMatcher(pattern.lowercase()) != null

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

    /**
     * Compiled globs, keyed by the lowercased pattern. Matching happens once per file per
     * pattern while the tree is built and on every keystroke in the preview, so compiling
     * a fresh [PathMatcher] each time is wasteful.
     *
     * A pattern that fails to compile caches [Optional.empty] rather than null —
     * [ConcurrentHashMap] forbids null values and `computeIfAbsent` would throw.
     */
    private val matcherCache = ConcurrentHashMap<String, Optional<PathMatcher>>()

    private fun compileMatcher(lowercasePattern: String): PathMatcher? =
        matcherCache.computeIfAbsent(lowercasePattern) { pattern ->
            try {
                Optional.of(FileSystems.getDefault().getPathMatcher("glob:$pattern"))
            } catch (_: Exception) {
                Optional.empty()
            }
        }.orElse(null)

    private fun matchGlob(pattern: String, target: String): Boolean {
        val matcher = compileMatcher(pattern) ?: return false
        return try {
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
     * Gets all project files from the entire project that match configured patterns,
     * using the union of every group's patterns. See [collectMatchingFiles].
     *
     * @param project The project to search in
     * @return List of (VirtualFile, relativePath) pairs for all matching files
     */
    fun getAllProjectFilesInProject(project: Project): List<Pair<VirtualFile, String>> =
        sweep(project, AndroidViewSettings.getInstance().projectFileGroups).pool

    /**
     * The candidate pool for a given pattern union: every file reachable from a module
     * content root or from a path-based pattern's directory prefix that matches at least
     * one inclusion pattern.
     *
     * Exclusions in [patterns] are ignored here on purpose. Discovery is shared across all
     * groups, so one group's `!` must not hide a file another group includes — per-group
     * exclusions are applied downstream by whoever filters this list.
     *
     * Must be called inside a read action: this touches the VFS.
     *
     * @return List of (VirtualFile, relativePath) pairs
     */
    fun collectMatchingFiles(project: Project, patterns: List<String>): List<Pair<VirtualFile, String>> =
        discover(project, patterns.filter { !it.startsWith("!") })
            .distinctBy { it.file }
            .map { it.file to it.relativePath }

    /** A candidate file, with the module whose content root it was found directly under. */
    private class Discovered(
        val file: VirtualFile,
        val relativePath: String,
        /** null when the file came from a path-pattern scan rather than a content root. */
        val contentRootOwner: Module?
    )

    /**
     * The one walk [collectMatchingFiles] and [sweep] are both built on.
     *
     * Keeping it single is what stops the tree and the group preview drifting apart: they need
     * different projections of the same discovery, not two implementations of it. A file found
     * under more than one content root appears once per owning module, so callers that care about
     * attribution see all of them and callers that want the pool deduplicate by file.
     *
     * Must be called inside a read action: this touches the VFS.
     */
    private fun discover(project: Project, inclusionPatterns: List<String>): List<Discovered> {
        if (inclusionPatterns.isEmpty()) return emptyList()

        val projectBaseDir = project.basePath
            ?.let { LocalFileSystem.getInstance().findFileByPath(it) }
            ?: return emptyList()

        val result = mutableListOf<Discovered>()
        val seen = mutableSetOf<VirtualFile>()

        for (module in ModuleManager.getInstance(project).modules) {
            if (module.isDisposed) continue

            for (root in ModuleRootManager.getInstance(module).contentRoots) {
                // Immediate children only, which is why a nested content root's files reach the
                // inner module alone.
                for (child in root.children) {
                    if (!child.isValid || child.isDirectory) continue

                    val relativePath = VfsUtil.getRelativePath(child, projectBaseDir, '/') ?: child.name
                    if (!matchesPatterns(child.name, relativePath, inclusionPatterns)) continue

                    seen.add(child)
                    result.add(Discovered(child, relativePath, module))
                }
            }
        }

        // Also scan directories pointed to directly by path-based patterns.
        // This handles non-Gradle-module folders (e.g. a top-level "docs/" folder).
        for (entry in getFilesFromPathBasedPatterns(projectBaseDir, inclusionPatterns)) {
            if (seen.add(entry.first)) {
                result.add(Discovered(entry.first, entry.second, null))
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
            val targetDir = resolveDirCaseInsensitive(projectBaseDir, prefix) ?: continue
            if (!targetDir.isValid || !targetDir.isDirectory) continue
            scanDirectoryForFiles(targetDir, projectBaseDir, patterns, result)
        }
        return result
    }

    private fun resolveDirCaseInsensitive(base: VirtualFile, relative: String): VirtualFile? {
        var current = base
        for (segment in relative.split('/')) {
            if (segment.isEmpty()) continue
            current = current.children.firstOrNull {
                it.isDirectory && it.name.equals(segment, ignoreCase = true)
            } ?: return null
        }
        return current
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
                if (child.name !in IGNORED_SCAN_DIRECTORIES) {
                    scanDirectoryForFiles(child, projectBaseDir, patterns, result)
                }
            } else {
                val relativePath = VfsUtil.getRelativePath(child, projectBaseDir, '/') ?: child.name
                if (matchesPatterns(child.name, relativePath, patterns)) {
                    result.add(child to relativePath)
                }
            }
        }
    }

    private val IGNORED_SCAN_DIRECTORIES = setOf(
        BUILD_DIRECTORY_NAME, ".git", ".gradle", ".idea", "node_modules"
    )
}
