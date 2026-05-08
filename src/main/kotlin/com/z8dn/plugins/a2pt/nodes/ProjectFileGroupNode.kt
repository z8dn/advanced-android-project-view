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
import com.z8dn.plugins.a2pt.utils.GroupIconResolver
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
    private val allProjectFiles: List<Pair<VirtualFile, String>>
) : AbstractTreeNode<String>(myProject, fileGroup.groupName) {

    override fun getChildren(): Collection<AbstractTreeNode<*>> {
        // Only show children if showProjectFilesInModule is false
        if (AndroidViewNodeUtils.showProjectFilesInModule()) {
            return emptyList()
        }

        val result = mutableListOf<AbstractTreeNode<*>>()
        val psiManager = PsiManager.getInstance(myProject)

        for ((file, relativePath) in allProjectFiles) {
            // Only include files that match this group's patterns
            if (!AndroidViewNodeUtils.matchesPatterns(file.name, relativePath, fileGroup.patterns)) {
                continue
            }

            val psiFile = psiManager.findFile(file) ?: continue

            // Find which module contains this file (may be null for non-module folders)
            val module = ModuleUtilCore.findModuleForFile(file, myProject)
            val qualifier = if (module != null) {
                ProjectFileDisplayUtils.generateDisplayName(file, module)
            } else {
                file.parent?.name ?: relativePath
            }
            result.add(ProjectFileNode(myProject, psiFile, settings, qualifier, 10))
        }

        return result
    }

    override fun update(data: PresentationData) {
        data.presentableText = fileGroup.groupName
        data.setIcon(getGroupIcon())
    }

    private fun getGroupIcon(): Icon =
        GroupIconResolver.resolve(fileGroup.iconKey, ::autoDetectIcon)

    private fun autoDetectIcon(): Icon {
        val inclusionPatterns = fileGroup.patterns.filter { !it.startsWith("!") }
        if (inclusionPatterns.size == 1) {
            val pattern = inclusionPatterns[0]
            val fileTypeManager = FileTypeManager.getInstance()

            if (pattern.startsWith("*.")) {
                val extension = pattern.substring(2)
                val fileType = fileTypeManager.getFileTypeByExtension(extension)
                return fileType.icon ?: AllIcons.FileTypes.Text
            }

            if (!pattern.contains("*") && !pattern.contains("/")) {
                val fileType = fileTypeManager.getFileTypeByFileName(pattern)
                return fileType.icon ?: AllIcons.FileTypes.Text
            }
        }

        return AllIcons.Nodes.Folder
    }
}
