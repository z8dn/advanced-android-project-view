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
 * Consolidated provider that adds custom nodes (build directory and README files) to the Android Project View.
 *
 * This provider handles both build directories and README files for Android and non-Android modules.
 * For non-Android modules, it returns a single wrapper node that aggregates all custom children,
 * preventing duplicate node creation when multiple features are enabled.
 */
class AndroidViewCustomNodesProvider : AndroidViewNodeProvider {

    /**
     * Provides custom children (build directory and README files) for Android modules.
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

        // Add README file if enabled
        if (androidViewSettings.showReadme) {
            val readmeFile = AndroidViewNodeUtils.findReadmeFile(module)
            if (readmeFile != null) {
                val readmePsi = psiManager.findFile(readmeFile)
                if (readmePsi != null) {
                    nodes.add(PsiFileNode(module.project, readmePsi, settings))
                }
            }
        }

        return nodes.ifEmpty { null }
    }

    /**
     * Provides a single wrapped module node for non-Android modules.
     *
     * This method returns a single [NonAndroidModuleWithCustomNodes] wrapper that aggregates
     * both build directory and README children, preventing duplicate nodes when both features are enabled.
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

        val readmeFile = if (androidViewSettings.showReadme) {
            AndroidViewNodeUtils.findReadmeFile(module)
        } else null

        // Only return wrapper if at least one file exists
        if (buildDir == null && readmeFile == null) {
            return null
        }

        // Return single wrapper with precomputed file results
        return listOf(NonAndroidModuleWithCustomNodes(module.project, module, settings, buildDir, readmeFile))
    }
}
