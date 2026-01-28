package com.z8dn.plugins.a2pt

import com.android.tools.idea.navigator.nodes.AndroidViewNodeProvider
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
 * Wrapper for Gradle module nodes that adds project file nodes.
 *
 * This class extends [NonAndroidModuleNode] to inject project files into Gradle modules
 * (non-Android modules) when the feature is enabled. Note that build directories are only
 * shown for Android modules, not Gradle modules.
 *
 * @param project The project containing the module
 * @param module The Gradle module to wrap
 * @param settings View settings for rendering nodes
 */
class GradleModuleWithProjectFiles(
    project: Project,
    module: Module,
    settings: ViewSettings
) : NonAndroidModuleNode(project, module, settings) {

    /**
     * Returns the module's children, including project files.
     *
     * @return Collection of child nodes including default children and project file additions
     */
    override fun getModuleChildren(): Collection<AbstractTreeNode<*>> {
        // 1. Get default children from parent NonAndroidModuleNode
        //    This includes: NonAndroidSourceTypeNode, AndroidBuildScriptNode (if enabled)
        val children = super.getModuleChildren().toMutableList()

        // 2. Ask all AndroidViewNodeProvider implementations for additional children
        //    This is what AndroidModuleNode does but NonAndroidModuleNode doesn't!
        val module = value ?: return children
        if (module.isDisposed) return children

        AndroidViewNodeProvider.getProviders().forEach { provider ->
            // Each provider can contribute children (e.g., ProjectFileNode)
            provider.getModuleChildren(module, settings)?.let { providerChildren ->
                children.addAll(providerChildren)
            }
        }

        return children
    }
}
