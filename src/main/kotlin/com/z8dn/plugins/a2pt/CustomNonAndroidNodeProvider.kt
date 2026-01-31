package com.z8dn.plugins.a2pt

import com.android.tools.idea.navigator.nodes.other.NonAndroidModuleNode
import com.android.tools.idea.projectsystem.gradle.getGradleIdentityPath
import com.android.tools.idea.projectsystem.gradle.getGradleProjectPath
import com.android.tools.idea.projectsystem.gradle.toHolder
import com.intellij.ide.projectView.TreeStructureProvider
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.util.treeView.AbstractTreeNode
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
        projectFiles.forEach { fileInfo ->
            val psiFile = psiManager.findFile(fileInfo.file)
            if (psiFile != null && !AndroidViewNodeUtils.showInProjectFilesGroup()) {
                modified.add(ProjectFileNode(project, psiFile, settings ?: parent.settings, fileInfo.displayName, 10))
            }
        }

        return modified
    }

    /**
     * Data class to hold file information with display name.
     * Similar to BuildConfigurationSourceProvider.ConfigurationFile
     */
    private data class ProjectFileInfo(
        val file: VirtualFile,
        val displayName: String?
    )

    /**
     * Gets all project files for a module with their display names.
     * Filters project files from the entire project to those contained in this module.
     */
    private fun getProjectFiles(module: Module): List<ProjectFileInfo> {
        val allProjectFiles = AndroidViewNodeUtils.getAllProjectFilesInProject(module.project)
        return allProjectFiles
            .filter {
                ModuleUtilCore.moduleContainsFile(module, it, true) ||
                ModuleUtilCore.moduleContainsFile(module, it, false)
            }
            .map { file ->
                // Generate display name similar to BuildConfigurationSourceProvider
                val displayName = generateDisplayName(file, module)
                ProjectFileInfo(file, displayName)
            }
    }

    /**
     * Generates a display name for a file based on its type and module.
     * Returns null for files that shouldn't show a qualifier (Proguard, Gradle).
     *
     * Similar to BuildConfigurationSourceProvider.ConfigurationFile.getDisplayName()
     */
    private fun generateDisplayName(file: VirtualFile, module: Module): String? {
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
