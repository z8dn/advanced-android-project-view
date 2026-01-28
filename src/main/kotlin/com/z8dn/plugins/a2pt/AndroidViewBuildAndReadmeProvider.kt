package com.z8dn.plugins.a2pt

import com.android.tools.idea.navigator.nodes.AndroidViewNodeProvider
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode
import com.intellij.ide.projectView.impl.nodes.PsiFileNode
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.module.Module
import com.intellij.psi.PsiManager
import org.jetbrains.android.facet.AndroidFacet

/**
 * Provider that adds build directory and custom files to the Android Project View.
 *
 * For Android modules: Adds build directory (if enabled) and custom files directly as children.
 * For Gradle modules: Wraps the module to add custom files (build directories not shown for Gradle modules).
 */
class AndroidViewBuildAndReadmeProvider : AndroidViewNodeProvider {


    /**
     * Provides build directory and custom files for Android modules.
     *
     * @param module The Android module to get children for
     * @param settings View settings for rendering nodes
     * @param List containing build dir and/or custom file nodes, or null if nothing to add
     */
    override fun getModuleChildren(
        module: Module,
        settings: ViewSettings
    ): List<AbstractTreeNode<*>>? {
        if (module.isDisposed) return null

        // Only provide nodes for Android modules
        if (AndroidFacet.getInstance(module) == null) {
            return null
        }

        val androidViewSettings = AndroidViewSettings.getInstance()
        val nodes = mutableListOf<AbstractTreeNode<*>>()
        val psiManager = PsiManager.getInstance(module.project)

        // Add a build directory if enabled
        if (androidViewSettings.showBuildDirectory) {
            val buildDir = AndroidViewNodeUtils.findBuildDirectory(module)
            if (buildDir != null) {
                val buildDirPsi = psiManager.findDirectory(buildDir)
                if (buildDirPsi != null) {
                    nodes.add(PsiDirectoryNode(module.project, buildDirPsi, settings))
                }
            }
        }

        // Add custom files if showing in modules
        if (AndroidViewNodeUtils.showProjectFilesInModule()) {
            val projectFilesByGroup = AndroidViewNodeUtils.findAllProjectFilesByGroup(module)
            for ((_, projectFiles) in projectFilesByGroup) {
                for (projectFile in projectFiles) {
                    val psiFile = psiManager.findFile(projectFile)
                    if (psiFile != null) {
                        nodes.add(ProjectFileNode(module.project, psiFile, settings, null, 0))
                    }
                }
            }
        }

        return nodes.ifEmpty { null }
    }

    /**
     * Provides a wrapper node for Gradle modules (non-Android modules) to add custom files.
     *
     * This method returns a [GradleModuleWithProjectFiles] wrapper that adds custom files to Gradle modules.
     * Note: Build directories are NOT shown for Gradle modules, only for Android modules.
     *
     * @param module The Gradle module to wrap
     * @param settings View settings for rendering nodes
     * @return List containing the wrapped module node, or null if no custom files to show
     */
    override fun getModuleNodes(
        module: Module,
        settings: ViewSettings
    ): List<AbstractTreeNode<*>>? {
        if (module.isDisposed) return null

        // Only handle Gradle modules (non-Android modules)
        if (AndroidFacet.getInstance(module) != null) {
            return null // Android modules handled by getModuleChildren()
        }

        val androidViewSettings = AndroidViewSettings.getInstance()

        // Check if there are custom files to show
        val hasProjectFiles = if (AndroidViewNodeUtils.showProjectFilesInModule()) {
            AndroidViewNodeUtils.findAllProjectFilesByGroup(module).isNotEmpty()
        } else false

        // Only return wrapper if there are custom files to show
        if (!hasProjectFiles) {
            return null
        }

        // Return single wrapper for Gradle module with custom files support
        return listOf(GradleModuleWithProjectFiles(module.project, module, settings))
    }
}
