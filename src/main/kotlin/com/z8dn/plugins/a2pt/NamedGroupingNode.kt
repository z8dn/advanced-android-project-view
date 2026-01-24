package com.z8dn.plugins.a2pt

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager

/**
 * A named grouping node that shows custom files under a specific group name.
 * This represents a single grouping configuration within the "Custom Nodes" section.
 *
 * @param project The project
 * @param groupName The name of this grouping
 * @param files List of pairs (Module, VirtualFile) representing files in this group
 * @param settings View settings for rendering nodes
 */
class NamedGroupingNode(
    project: Project,
    private val groupName: String,
    private val files: List<Pair<Module, VirtualFile>>,
    private val settings: ViewSettings
) : ProjectViewNode<String>(project, groupName, settings) {

    override fun getChildren(): Collection<AbstractTreeNode<*>> {
        val project = myProject ?: return emptyList()
        val psiManager = PsiManager.getInstance(project)
        val children = mutableListOf<AbstractTreeNode<*>>()

        for ((module, file) in files) {
            if (module.isDisposed) continue

            val psiFile = psiManager.findFile(file)
            if (psiFile != null) {
                children.add(CustomFileNodeWithModule(project, psiFile, module, settings))
            }
        }

        return children.distinctBy {
            (it as? CustomFileNodeWithModule)?.virtualFile?.path
        }
    }

    override fun update(presentation: PresentationData) {
        presentation.presentableText = groupName
    }

    override fun contains(file: VirtualFile): Boolean {
        return files.any { it.second.path == file.path }
    }
}
