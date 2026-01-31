package com.z8dn.plugins.a2pt

import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
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
            // Only include files that match this group's patterns
            if (!matchesAnyPattern(file.name, fileGroup.patterns)) {
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

    /**
     * Determines the icon for this group based on the patterns.
     * - If there's only one pattern, use a file-type-specific icon
     * - If there are multiple patterns, use a generic folder icon
     */
    private fun getGroupIcon(): Icon {
        if (fileGroup.patterns.size == 1) {
            val pattern = fileGroup.patterns[0]
            val fileTypeManager = FileTypeManager.getInstance()

            // Handle wildcard patterns like "*.md"
            if (pattern.startsWith("*.")) {
                val extension = pattern.substring(2)
                val fileType = fileTypeManager.getFileTypeByExtension(extension)
                return fileType.icon ?: AllIcons.FileTypes.Text
            }

            // Handle exact filename patterns like "LICENSE"
            if (!pattern.contains("*")) {
                val fileType = fileTypeManager.getFileTypeByFileName(pattern)
                return fileType.icon ?: AllIcons.FileTypes.Text
            }
        }

        // Default to folder icon for multiple patterns or complex wildcards
        return AllIcons.Nodes.Folder
    }

    /**
     * Checks if a filename matches any of the specified patterns.
     */
    private fun matchesAnyPattern(filename: String, patterns: List<String>): Boolean {
        for (pattern in patterns) {
            if (ProjectFileDisplayUtils.matchesPattern(filename, pattern)) {
                return true
            }
        }
        return false
    }
}
