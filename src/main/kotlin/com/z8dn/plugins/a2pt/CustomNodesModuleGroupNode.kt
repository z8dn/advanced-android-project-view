package com.z8dn.plugins.a2pt

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.nodes.PsiFileNode
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager

/**
 * A module grouping node that shows custom files for a specific module.
 * This node represents a module within the "Custom Nodes" section.
 *
 * @param project The project
 * @param module The module this node represents
 * @param settings View settings for rendering nodes
 * @param customFiles The custom files belonging to this module
 */
class CustomNodesModuleGroupNode(
    project: Project,
    private val module: Module,
    private val settings: ViewSettings,
    private val customFiles: List<VirtualFile>
) : ProjectViewNode<Module>(project, module, settings) {

    override fun getChildren(): Collection<AbstractTreeNode<*>> {
        if (module.isDisposed) return emptyList()

        val project = myProject ?: return emptyList()
        val psiManager = PsiManager.getInstance(project)
        val children = mutableListOf<AbstractTreeNode<*>>()

        for (file in customFiles) {
            val psiFile = psiManager.findFile(file)
            if (psiFile != null) {
                children.add(PsiFileNode(project, psiFile, settings))
            }
        }

        return children
    }

    override fun update(presentation: PresentationData) {
        presentation.presentableText = module.name
    }

    override fun contains(file: VirtualFile): Boolean {
        return customFiles.any { it.path == file.path }
    }
}
