package com.z8dn.plugins.a2pt.providers

import com.z8dn.plugins.a2pt.index.ProjectFileGroupIndex
import com.z8dn.plugins.a2pt.nodes.ProjectFileNode
import com.z8dn.plugins.a2pt.utils.AndroidViewNodeUtils

import com.android.tools.idea.navigator.nodes.other.NonAndroidModuleNode
import com.intellij.ide.projectView.TreeStructureProvider
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.psi.PsiManager

/**
 * TreeStructureProvider that adds project files to non-Android module nodes.
 *
 * This provider intercepts NonAndroidModuleNode instances in the Android Project View
 * and adds ProjectFileNode children for files matching the configured patterns.
 */
class CustomNonAndroidNodeProvider : TreeStructureProvider {

    override fun modify(
        parent: AbstractTreeNode<*>,
        children: Collection<AbstractTreeNode<*>>,
        settings: ViewSettings?
    ): Collection<AbstractTreeNode<*>> {
        // Only modify NonAndroidModuleNode (Gradle modules)
        if (parent !is NonAndroidModuleNode) {
            return children
        }

        // Only show project files in modules if showProjectFilesInModule is true
        if (!AndroidViewNodeUtils.showProjectFilesInModule()) {
            return children
        }

        val module = parent.value ?: return children
        val project = parent.project ?: return children

        val modified = ArrayList(children)
        val psiManager = PsiManager.getInstance(project)

        // Get project files for this module
        val projectFiles = ProjectFileGroupIndex.getInstance(project).filesFor(module)

        // Add ProjectFileNode for each file (no qualifier needed when files are in their own modules)
        projectFiles.forEach { file ->
            val psiFile = psiManager.findFile(file)
            if (psiFile != null) {
                modified.add(ProjectFileNode(project, psiFile, settings ?: parent.settings, null, 10))
            }
        }

        return modified
    }
}
