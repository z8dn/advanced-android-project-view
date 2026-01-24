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
 * Consolidated provider that adds custom nodes (build directory and custom files) to the Android Project View.
 *
 * This provider handles both build directories and custom files for Android and non-Android modules.
 * For non-Android modules, it returns a single wrapper node that aggregates all custom children,
 * preventing duplicate node creation when multiple features are enabled.
 */
class AndroidViewCustomNodesProvider : AndroidViewNodeProvider {


    /**
     * Provides custom children (build directory and custom files) for Android modules.
     *
     * @param module The module to get children for
     * @param settings View settings for rendering nodes
     * @return List containing custom nodes or null if no features are enabled or files not found
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

        // Add custom files matching the configured patterns if enabled and NOT grouped
        if (androidViewSettings.showCustomFiles &&
            androidViewSettings.filePatterns.isNotEmpty() &&
            !androidViewSettings.groupCustomNodes) {
            val matchingFiles = AndroidViewNodeUtils.findMatchingFiles(module, androidViewSettings.filePatterns)
            for (file in matchingFiles) {
                val psiFile = psiManager.findFile(file)
                if (psiFile != null) {
                    nodes.add(PsiFileNode(module.project, psiFile, settings))
                }
            }
        }

        return nodes.ifEmpty { null }
    }

    /**
     * Provides a single wrapped module node for non-Android modules.
     *
     * This method returns a single [NonAndroidModuleWithCustomNodes] wrapper that aggregates
     * both build directory and custom file children, preventing duplicate nodes when both features are enabled.
     *
     * @param module The module to get nodes for
     * @param settings View settings for rendering nodes
     * @return List containing the wrapped module node, or null if not applicable
     */
    override fun getModuleNodes(
        module: Module,
        settings: ViewSettings
    ): List<AbstractTreeNode<*>>? {
        if (module.isDisposed) return null

        // Only handle non-Android modules
        if (AndroidFacet.getInstance(module) != null) {
            return null // Android modules handled by getModuleChildren()
        }

        val androidViewSettings = AndroidViewSettings.getInstance()

        // Find files once and reuse the results
        val buildDir = if (androidViewSettings.showBuildDirectory) {
            AndroidViewNodeUtils.findBuildDirectory(module)
        } else null

        val customFiles = if (androidViewSettings.showCustomFiles &&
                                androidViewSettings.filePatterns.isNotEmpty() &&
                                !androidViewSettings.groupCustomNodes) {
            AndroidViewNodeUtils.findMatchingFiles(module, androidViewSettings.filePatterns)
        } else emptyList()

        // Only return wrapper if at least one file exists
        if (buildDir == null && customFiles.isEmpty()) {
            return null
        }

        // Return single wrapper with precomputed file results
        return listOf(NonAndroidModuleWithCustomNodes(module.project, module, settings, buildDir, customFiles))
    }
}
