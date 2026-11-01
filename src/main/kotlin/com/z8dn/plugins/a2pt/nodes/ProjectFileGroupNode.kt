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
 *
 * @param groupFiles the files this group claims, already filtered by
 *   [com.z8dn.plugins.a2pt.index.ProjectFileGroupIndex] with the group's exclusions applied,
 *   each paired with its path relative to the project root
 */
class ProjectFileGroupNode(
    private val myProject: Project,
    private val settings: ViewSettings,
    private val fileGroup: ProjectFileGroup,
    private val groupFiles: List<Pair<VirtualFile, String>>
) : AbstractTreeNode<String>(myProject, fileGroup.groupName) {

    override fun getChildren(): Collection<AbstractTreeNode<*>> {
        // Only show children if showProjectFilesInModule is false
        if (AndroidViewNodeUtils.showProjectFilesInModule()) {
            return emptyList()
        }

        val result = mutableListOf<AbstractTreeNode<*>>()
        val psiManager = PsiManager.getInstance(myProject)

        for ((file, relativePath) in groupFiles) {
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

    /**
     * Determines the icon for this group based on the patterns.
     * - If a custom icon is set, use that
     * - If there's only one non-exclusion pattern, use a file-type-specific icon
     * - Otherwise, use a generic folder icon
     */
    private fun getGroupIcon(): Icon {
        // Check if a custom icon is set
        if (fileGroup.customIconPath != null) {
            return getIconFromPath(fileGroup.customIconPath!!)
        }

        val inclusionPatterns = fileGroup.patterns.filter { !it.startsWith("!") }
        if (inclusionPatterns.size == 1) {
            val pattern = inclusionPatterns[0]
            val fileTypeManager = FileTypeManager.getInstance()

            // Handle wildcard patterns like "*.md"
            if (pattern.startsWith("*.")) {
                val extension = pattern.substring(2)
                val fileType = fileTypeManager.getFileTypeByExtension(extension)
                return fileType.icon ?: AllIcons.FileTypes.Text
            }

            // Handle exact filename patterns like "LICENSE"
            if (!pattern.contains("*") && !pattern.contains("/")) {
                val fileType = fileTypeManager.getFileTypeByFileName(pattern)
                return fileType.icon ?: AllIcons.FileTypes.Text
            }
        }

        // Default to folder icon for multiple patterns or complex wildcards
        return AllIcons.Nodes.Folder
    }

    /**
     * Resolves an icon from the AllIcons path string.
     * Example: "AllIcons.Nodes.Folder" -> AllIcons.Nodes.Folder
     */
    private fun getIconFromPath(iconPath: String): Icon {
        return try {
            // Parse the icon path and use reflection to get the icon
            when (iconPath) {
                "AllIcons.Nodes.Folder" -> AllIcons.Nodes.Folder
                "AllIcons.Nodes.Package" -> AllIcons.Nodes.Package
                "AllIcons.Nodes.Module" -> AllIcons.Nodes.Module
                "AllIcons.Nodes.ConfigFolder" -> AllIcons.Nodes.ConfigFolder
                "AllIcons.Nodes.DataTables" -> AllIcons.Nodes.DataTables
                "AllIcons.Nodes.ResourceBundle" -> AllIcons.Nodes.ResourceBundle
                "AllIcons.Nodes.WebFolder" -> AllIcons.Nodes.WebFolder
                "AllIcons.General.Settings" -> AllIcons.General.Settings
                "AllIcons.General.GearPlain" -> AllIcons.General.GearPlain
                "AllIcons.Actions.ListFiles" -> AllIcons.Actions.ListFiles
                "AllIcons.Actions.GroupBy" -> AllIcons.Actions.GroupBy
                "AllIcons.Actions.Copy" -> AllIcons.Actions.Copy
                "AllIcons.Actions.Edit" -> AllIcons.Actions.Edit
                "AllIcons.FileTypes.Config" -> AllIcons.FileTypes.Config
                "AllIcons.FileTypes.Text" -> AllIcons.FileTypes.Text
                "AllIcons.FileTypes.Properties" -> AllIcons.FileTypes.Properties
                "AllIcons.FileTypes.Archive" -> AllIcons.FileTypes.Archive
                else -> AllIcons.Nodes.Folder // Default fallback
            }
        } catch (e: Exception) {
            AllIcons.Nodes.Folder // Fallback in case of error
        }
    }
}
