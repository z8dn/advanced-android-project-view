package com.z8dn.plugins.a2pt

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
    private val README_FILE_VARIATIONS = listOf("README.md", "readme.md", "Readme.md", "README.MD")

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
     * Finds a README file in the module's content roots.
     * Searches for common README filename variations.
     *
     * @param module The module to search in
     * @return The README file VirtualFile, or null if not found or module is disposed
     */
    fun findReadmeFile(module: Module): VirtualFile? {
        if (module.isDisposed) return null

        val contentRoots = ModuleRootManager.getInstance(module).contentRoots

        for (root in contentRoots) {
            for (variation in README_FILE_VARIATIONS) {
                val readme = root.findChild(variation)
                if (readme != null && readme.isValid && !readme.isDirectory) {
                    return readme
                }
            }
        }
        return null
    }
}
