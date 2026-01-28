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
        // This provides children for BOTH Android and non-Android modules
        // For Android modules: called directly by AndroidModuleNode
        // For non-Android modules: called by GradleModuleWithProjectFiles

        if (!AndroidViewNodeUtils.showProjectFilesInModule()) {
            return null
        }

        val children = mutableListOf<AbstractTreeNode<*>>()
        val psiManager = PsiManager.getInstance(module.project)

        // Add project files
        val projectFilesByGroup = AndroidViewNodeUtils.findAllProjectFilesByGroup(module)
        for ((_, projectFiles) in projectFilesByGroup) {
            for (projectFile in projectFiles) {
                val psiFile = psiManager.findFile(projectFile)
                if (psiFile != null) {
                    children.add(
                        ProjectFileNode(
                            module.project,
                            psiFile,
                            settings,
                            null,  // qualifier
                            90     // order (before build files at 100+)
                        )
                    )
                }
            }
        }

        return children.ifEmpty { null }
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

        // Only handle non-Android modules (Gradle modules without AndroidFacet)
        if (AndroidFacet.getInstance(module) != null) {
            return null // Android modules handled by AndroidViewNodeDefaultProvider
        }

        // Check if setting is enabled
        if (!AndroidViewNodeUtils.showProjectFilesInModule()) {
            return null // Feature disabled, don't replace the node
        }

        // Check if there are custom files to show
        val hasProjectFiles = AndroidViewNodeUtils.findAllProjectFilesByGroup(module).isNotEmpty()

        // Only return wrapper if there are custom files to show
        if (!hasProjectFiles) {
            return null // No files to show, use default NonAndroidModuleNode
        }

        // Return enhanced wrapper that will aggregate all provider contributions
        return listOf(GradleModuleWithProjectFiles(module.project, module, settings))
    }
}
