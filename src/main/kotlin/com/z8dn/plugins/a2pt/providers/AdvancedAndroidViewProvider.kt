package com.z8dn.plugins.a2pt.providers

import com.android.tools.idea.apk.ApkFacet
import com.android.tools.idea.model.AndroidModel
import com.android.tools.idea.navigator.nodes.AndroidViewNodeProvider
import com.android.tools.idea.navigator.nodes.android.AndroidModuleNode
import com.android.tools.idea.projectsystem.getModuleSystem
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.z8dn.plugins.a2pt.nodes.GradleModuleWithProjectFiles
import com.z8dn.plugins.a2pt.nodes.ProjectFileNode
import com.z8dn.plugins.a2pt.settings.AndroidViewSettings
import com.z8dn.plugins.a2pt.utils.AndroidViewNodeUtils
import org.jetbrains.android.facet.AndroidFacet

/**
 * Provider that adds build directory and custom files to the Android Project View.
 *
 * For Android modules: Adds build directory (if enabled) and custom files directly as children.
 * For Gradle modules: Wraps the module to add custom files (build directories not shown for Gradle modules).
 */
class AdvancedAndroidViewProvider : AndroidViewNodeProvider {


    /**
     * Provides build directory and custom files for Android modules.
     *
     * @param module The Android module to get children for
     * @param settings View settings for rendering nodes
     * @param List containing build dir and/or custom file nodes, or null if nothing to add
     */
    override fun getModuleChildren(
        module: Module,
        settings: ViewSettings
    ): List<AbstractTreeNode<*>>? {
        val result = mutableListOf<AbstractTreeNode<*>>()
        val facet = AndroidFacet.getInstance(module) ?: return null
        val project = facet.module.project

        val moduleSystem = module.getModuleSystem()
        val sampleDataPsi = AndroidModuleNode.getPsiDirectory(module.project, moduleSystem.getSampleDataDirectory())
        if (sampleDataPsi != null) {
            result.add(PsiDirectoryNode(module.project, sampleDataPsi, settings))
        }

        val androidViewSettings = AndroidViewSettings.getInstance()

        // Add a build directory if enabled
        if (androidViewSettings.showBuildDirectory) {
            val psiManager = PsiManager.getInstance(module.project)
            val buildDir = AndroidViewNodeUtils.findBuildDirectory(module)
            if (buildDir != null) {
                val buildDirPsi = psiManager.findDirectory(buildDir)
                if (buildDirPsi != null) {
                    result.add(PsiDirectoryNode(module.project, buildDirPsi, settings))
                }
            }
        }

        // Only show project files in modules if showProjectFilesInModule is true
        if (AndroidViewNodeUtils.showProjectFilesInModule()) {
            val psiManager = PsiManager.getInstance(project)
            getProjectFiles(module).forEach { file ->
                val psiFile = psiManager.findFile(file)
                if (psiFile != null) {
                    // No qualifier needed when files are shown in their own modules
                    result.add(ProjectFileNode(project, psiFile, settings, null, 10))
                }
            }
        }
        return result
    }

    override fun getModuleNodes(module: Module, settings: ViewSettings): List<AbstractTreeNode<*>>? {
        val apkFacet = ApkFacet.getInstance(module)
        val androidFacet = AndroidFacet.getInstance(module)
        val project = module.project
        return when {
            androidFacet != null && apkFacet != null ->
                emptyList()

            androidFacet != null && AndroidModel.isRequired(androidFacet) ->
                emptyList()

            else -> listOf(GradleModuleWithProjectFiles(project, module, settings))
        }
    }

    /**
     * Gets all project files for a module, following the getBuildFiles pattern.
     * Gets all project files from the entire project, then filters to those contained in this module.
     *
     * @param module The module to filter files for
     * @return List of project files (VirtualFile) belonging to this module
     */
    private fun getProjectFiles(module: Module): List<VirtualFile> {
        val allProjectFiles = AndroidViewNodeUtils.getAllProjectFilesInProject(module.project)
        return allProjectFiles.filter {
            ModuleUtilCore.moduleContainsFile(module, it, true) || ModuleUtilCore.moduleContainsFile(
                module,
                it,
                false
            )
        }
    }

}
