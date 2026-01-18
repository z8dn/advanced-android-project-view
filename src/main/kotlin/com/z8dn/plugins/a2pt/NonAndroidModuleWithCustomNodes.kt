package com.z8dn.plugins.a2pt

import com.android.tools.idea.navigator.nodes.other.NonAndroidModuleNode
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode
import com.intellij.ide.projectView.impl.nodes.PsiFileNode
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager

/**
 * Custom wrapper for non-Android module nodes that adds build directory and README file nodes.
 *
 * This class extends [NonAndroidModuleNode] to inject additional child nodes (build directory
 * and README files) into non-Android Gradle modules when the corresponding features are enabled.
 *
 * @param project The project containing the module
 * @param module The module to wrap
 * @param settings View settings for rendering nodes
 * @param buildDir Precomputed build directory VirtualFile, or null if not available
 * @param readmeFile Precomputed README VirtualFile, or null if not available
 */
class NonAndroidModuleWithCustomNodes(
    project: Project,
    module: Module,
    settings: ViewSettings,
    private val buildDir: VirtualFile? = null,
    private val readmeFile: VirtualFile? = null
) : NonAndroidModuleNode(project, module, settings) {

    /**
     * Returns the module's children, including any custom nodes (build directory and README).
     *
     * @return Collection of child nodes including default children and custom additions
     */
    override fun getModuleChildren(): Collection<AbstractTreeNode<*>> {
        // Get default children from parent class
        val children = super.getModuleChildren().toMutableList()

        val module = value ?: return children
        if (module.isDisposed) return children

        val androidViewSettings = AndroidViewSettings.getInstance()
        val psiManager = PsiManager.getInstance(myProject)

        // Add build directory if enabled and provided
        if (androidViewSettings.showBuildDirectory && buildDir != null) {
            val psiDir = psiManager.findDirectory(buildDir)
            if (psiDir != null) {
                children.add(PsiDirectoryNode(myProject, psiDir, settings))
            }
        }

        // Add README file if enabled and provided
        if (androidViewSettings.showReadme && readmeFile != null) {
            val psiFile = psiManager.findFile(readmeFile)
            if (psiFile != null) {
                children.add(PsiFileNode(myProject, psiFile, settings))
            }
        }

        return children
    }
}
