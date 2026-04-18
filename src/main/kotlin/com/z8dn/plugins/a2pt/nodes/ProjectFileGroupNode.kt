package com.z8dn.plugins.a2pt.nodes

import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.z8dn.plugins.a2pt.settings.ProjectFileGroup
import com.z8dn.plugins.a2pt.utils.AndroidViewNodeUtils
import com.z8dn.plugins.a2pt.utils.ProjectFileDisplayUtils
import javax.swing.Icon

/**
 * A top-level group node that displays project files matching a specific ProjectFileGroup.
 * This node appears at the project root level when showProjectFilesInModule is false.
 * Each instance represents one ProjectFileGroup with its groupName and patterns.
 */
class ProjectFileGroupNode(
    private val myProject: Project,
    private val settings: ViewSettings,
    private val fileGroup: ProjectFileGroup,
    private val allProjectFiles: List<VirtualFile>
) : AbstractTreeNode<String>(myProject, fileGroup.groupName) {

    override fun getChildren(): Collection<AbstractTreeNode<*>> {
        // Only show children if showProjectFilesInModule is false
        if (AndroidViewNodeUtils.showProjectFilesInModule()) {
            return emptyList()
        }

        val result = mutableListOf<AbstractTreeNode<*>>()
        val psiManager = PsiManager.getInstance(myProject)

        for (file in allProjectFiles) {
            val relativePath = myProject.basePath
                ?.let { file.path.removePrefix(it).trimStart('/') }
                ?: file.name
            if (!ProjectFileDisplayUtils.matchesPatterns(file.name, relativePath, fileGroup.patterns)) {
                continue
            }

            val psiFile = psiManager.findFile(file) ?: continue

            // Find which module contains this file using efficient lookup
            val module = ModuleUtilCore.findModuleForFile(file, myProject) ?: continue

            val qualifier = ProjectFileDisplayUtils.generateDisplayName(file, module)
            result.add(ProjectFileNode(myProject, psiFile, settings, qualifier, 10))
        }

        return result
    }

    override fun update(data: PresentationData) {
        data.presentableText = fileGroup.groupName
        data.setIcon(getGroupIcon())
    }

    private fun getGroupIcon(): Icon {
        val inclusionPatterns = fileGroup.patterns.filter { !it.startsWith("!") }
        if (inclusionPatterns.size == 1) {
            val pattern = inclusionPatterns[0]
            val fileTypeManager = FileTypeManager.getInstance()
            if (pattern.startsWith("*.")) {
                val fileType = fileTypeManager.getFileTypeByExtension(pattern.substring(2))
                return fileType.icon ?: AllIcons.FileTypes.Text
            }
            if (!pattern.contains("*")) {
                val fileType = fileTypeManager.getFileTypeByFileName(pattern)
                return fileType.icon ?: AllIcons.FileTypes.Text
            }
        }
        return AllIcons.Nodes.Folder
    }
}
