//package com.z8dn.plugins.a2pt
//
//import com.intellij.ide.projectView.PresentationData
//import com.intellij.ide.projectView.ProjectViewNode
//import com.intellij.ide.projectView.ViewSettings
//import com.intellij.ide.util.treeView.AbstractTreeNode
//import com.intellij.openapi.module.ModuleManager
//import com.intellij.openapi.project.Project
//import com.intellij.psi.PsiDirectory
//import com.intellij.psi.PsiManager
//import com.intellij.icons.AllIcons
//import com.intellij.openapi.fileTypes.FileTypeManager
//import javax.swing.Icon
//
///**
// * Group node that displays project files at the project level in the Android Project View.
// *
// * This node appears when project files are not shown in individual modules,
// * aggregating all project files for a specific group.
// */
//class ProjectFilesGroupNode(
//    project: Project,
//    settings: ViewSettings,
//    private val group: ProjectFileGroup
//) : ProjectViewNode<ProjectFileGroup>(project, group, settings) {
//
//    override fun getChildren(): Collection<AbstractTreeNode<*>> {
//        val children = mutableListOf<AbstractTreeNode<*>>()
//        val psiManager = PsiManager.getInstance(myProject)
//
//        // Find all project files for this group in all modules
//        val modules = ModuleManager.getInstance(myProject).modules
//        for (module in modules) {
//            if (module.isDisposed) continue
//
//            val projectFiles = AndroidViewNodeUtils.findProjectFilesForGroup(module, group)
//            for (projectFile in projectFiles) {
//                val psiFile = psiManager.findFile(projectFile)
//                if (psiFile != null) {
//                    // Use only the last part of module name for shorter display
//                    val shortModuleName = module.name.substringAfterLast('.')
//                    children.add(ProjectFileNode(myProject, psiFile, settings, shortModuleName, 0))
//                }
//            }
//        }
//
//        return children
//    }
//
//    override fun update(presentation: PresentationData) {
//        presentation.presentableText = group.groupName
//        presentation.setIcon(getGroupIcon())
//    }
//
//    /**
//     * Gets the icon for this group.
//     * If the group has only one pattern, uses the icon from that file type.
//     * Otherwise, uses a default folder/group icon.
//     */
//    private fun getGroupIcon(): Icon {
//        // If only one pattern, try to get the file type icon
//        if (group.patterns.size == 1) {
//            val pattern = group.patterns[0]
//            val fileTypeManager = FileTypeManager.getInstance()
//
//            // Handle wildcard patterns like "*.md"
//            if (pattern.startsWith("*.")) {
//                val extension = pattern.substring(2)
//                val fileType = fileTypeManager.getFileTypeByExtension(extension)
//                return fileType.icon ?: AllIcons.FileTypes.Text
//            }
//
//            // Handle exact file names like "README.md"
//            if (!pattern.contains("*")) {
//                val fileType = fileTypeManager.getFileTypeByFileName(pattern)
//                return fileType.icon ?: AllIcons.FileTypes.Text
//            }
//        }
//
//        // Default icon for groups with multiple patterns
//        return AllIcons.Nodes.Folder
//    }
//
//    override fun getWeight(): Int = 100 // Show at end of tree
//
//    override fun contains(file: com.intellij.openapi.vfs.VirtualFile): Boolean = false
//}
