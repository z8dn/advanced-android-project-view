package com.z8dn.plugins.a2pt.utils

import com.z8dn.plugins.a2pt.settings.AndroidViewSettings

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VirtualFile

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
        if (settings.projectFileGroups.isEmpty()) return emptyList()

        val result = mutableSetOf<VirtualFile>()
        val moduleManager = com.intellij.openapi.module.ModuleManager.getInstance(project)

        for (module in moduleManager.modules) {
            if (module.isDisposed) continue

            val contentRoots = ModuleRootManager.getInstance(module).contentRoots
            for (root in contentRoots) {
                for (child in root.children) {
                    if (!child.isValid || child.isDirectory) continue
                    val relativePath = child.path.removePrefix(root.path).trimStart('/')
                    for (group in settings.projectFileGroups) {
                        if (ProjectFileDisplayUtils.matchesPatterns(child.name, relativePath, group.patterns)) {
                            result.add(child)
                            break
                        }
                    }
                }
            }
        }

        return result.toList()
    }
}
