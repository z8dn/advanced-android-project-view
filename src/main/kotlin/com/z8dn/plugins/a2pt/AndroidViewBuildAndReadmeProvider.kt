package com.z8dn.plugins.a2pt

import com.android.SdkConstants
import com.android.tools.idea.apk.ApkFacet
import com.android.tools.idea.model.AndroidModel
import com.android.tools.idea.navigator.nodes.AndroidViewNodeProvider
import com.android.tools.idea.navigator.nodes.android.AndroidModuleNode
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.projectsystem.gradle.getGradleIdentityPath
import com.android.tools.idea.projectsystem.gradle.getGradleProjectPath
import com.android.tools.idea.projectsystem.gradle.toHolder
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import org.jetbrains.android.facet.AndroidFacet

/**
 * Provider that adds build directory and custom files to the Android Project View.
 *
 * For Android modules: Adds build directory (if enabled) and custom files directly as children.
 * For Gradle modules: Wraps the module to add custom files (build directories not shown for Gradle modules).
 */
class AndroidViewBuildAndReadmeProvider : AndroidViewNodeProvider {


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

    /**
     * Generates a display name for a file based on its type and module.
     * Returns null for files that shouldn't show a qualifier (Proguard, Gradle).
     *
     * Similar to BuildConfigurationSourceProvider.ConfigurationFile.getDisplayName()
     */
    private fun generateDisplayName(file: VirtualFile, module: Module): String? {
        // Don't show qualifier for Proguard or Gradle files
        if (file.fileType == FileTypeRegistry.getInstance().findFileTypeByName("Shrinker Config File") ||
            file.extension.equals(SdkConstants.EXT_GRADLE)) {
            return null
        }

        // Get Gradle identity path and project path to determine the display name format
        val gradleIdentityPath = module.getGradleIdentityPath()
        val gradleProjectPath = module.getGradleProjectPath()?.toHolder()

        // Determine the project display name with appropriate prefix
        val projectDisplayName = when {
            gradleIdentityPath == ":" -> PROJECT_PREFIX + module.name
            gradleProjectPath?.path == ":" -> BUILD_PREFIX + gradleIdentityPath
            else -> MODULE_PREFIX + (gradleIdentityPath ?: module.name)
        }

        // Find matching group to get display name format
        val settings = AndroidViewSettings.getInstance()
        val matchingGroup = settings.projectFileGroups.find { group ->
            group.patterns.any { pattern ->
                matchesPattern(file.name, pattern)
            }
        }

        // Use pattern similar to BuildConfigurationSourceProvider:
        // - With group name: "GroupName for 'displayPath'"
        // - Without group name: Use the projectDisplayName directly
        return if (matchingGroup != null && matchingGroup.groupName.isNotEmpty()) {
            val displayPath = gradleIdentityPath ?: module.name
            displayPath
        } else {
            projectDisplayName
        }
    }

    /**
     * Checks if a filename matches a pattern (glob or exact match).
     */
    private fun matchesPattern(filename: String, pattern: String): Boolean {
        val filenameLower = filename.lowercase()
        val patternLower = pattern.lowercase()

        // Try glob pattern matching
        try {
            val matcher = java.nio.file.FileSystems.getDefault()
                .getPathMatcher("glob:$patternLower")
            val path = java.nio.file.FileSystems.getDefault().getPath(filenameLower)
            if (matcher.matches(path)) {
                return true
            }
        } catch (_: Exception) {
            // Fall through to exact match
        }

        // Exact match
        return filenameLower == patternLower
    }

    companion object {
        private const val MODULE_PREFIX = "Module "
        private const val PROJECT_PREFIX = "Project: "
        private const val BUILD_PREFIX = "Included build: "
    }
}
