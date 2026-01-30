package com.z8dn.plugins.a2pt

import com.android.SdkConstants
import com.android.tools.idea.navigator.nodes.other.NonAndroidModuleNode
import com.intellij.ide.projectView.TreeStructureProvider
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.vfs.VirtualFile
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

        // Check if feature is enabled
        if (!AndroidViewNodeUtils.showProjectFilesInModule()) {
            return children
        }

        val module = parent.value ?: return children
        val project = parent.project ?: return children

        val modified = ArrayList(children)
        val psiManager = PsiManager.getInstance(project)

        // Get project files for this module
        val projectFiles = getProjectFiles(module)

        // Add ProjectFileNode for each file
        projectFiles.forEach { file ->
            val psiFile = psiManager.findFile(file)
            if (psiFile != null && !AndroidViewNodeUtils.showInProjectFilesGroup()) {
                val qualifier = if (file.fileType == FileTypeRegistry.getInstance()
                        .findFileTypeByName("Shrinker Config File")
                    || file.extension.equals(SdkConstants.EXT_GRADLE)
                ) {
                    // No qualifier for proguard files or gradle files
                    null
                } else {
                    file.name
                }
                modified.add(ProjectFileNode(project, psiFile, settings ?: parent.settings, qualifier, 10))
            }
        }

        return modified
    }

    /**
     * Gets all project files for a module.
     * Filters project files from the entire project to those contained in this module.
     */
    private fun getProjectFiles(module: Module): List<VirtualFile> {
        val allProjectFiles = AndroidViewNodeUtils.getAllProjectFilesInProject(module.project)
        return allProjectFiles.filter {
            ModuleUtilCore.moduleContainsFile(module, it, true) ||
            ModuleUtilCore.moduleContainsFile(module, it, false)
        }
    }
}
