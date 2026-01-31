package com.z8dn.plugins.a2pt

import com.android.tools.idea.projectsystem.gradle.getGradleIdentityPath
import com.android.tools.idea.projectsystem.gradle.getGradleProjectPath
import com.android.tools.idea.projectsystem.gradle.toHolder
import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager

/**
 * A top-level group node that displays all project files from all modules.
 * This node appears at the project root level when showProjectFilesInModule is false.
 */
class ProjectFileGroupNode(
    private val myProject: Project,
    settings: ViewSettings
) : AbstractTreeNode<String>(myProject, "Project Files") {

    private val mySettings = settings

    override fun getChildren(): Collection<AbstractTreeNode<*>> {
        // Only show children if showProjectFilesInModule is false
        if (AndroidViewNodeUtils.showProjectFilesInModule()) {
            return emptyList()
        }

        val result = mutableListOf<AbstractTreeNode<*>>()
        val psiManager = PsiManager.getInstance(myProject)
        val allProjectFiles = AndroidViewNodeUtils.getAllProjectFilesInProject(myProject)

        // Group files by module for display name generation
        val moduleManager = ModuleManager.getInstance(myProject)

        for (file in allProjectFiles) {
            val psiFile = psiManager.findFile(file) ?: continue

            // Find which module contains this file
            val module = moduleManager.modules.firstOrNull { module ->
                ModuleUtilCore.moduleContainsFile(module, file, true) ||
                ModuleUtilCore.moduleContainsFile(module, file, false)
            }

            if (module != null) {
                val qualifier = generateDisplayName(file, module)
                result.add(ProjectFileNode(myProject, psiFile, mySettings, qualifier, 10))
            }
        }

        return result
    }

    override fun update(data: PresentationData) {
        data.presentableText = "Project Files"
        data.setIcon(AllIcons.Nodes.Folder)
    }

    /**
     * Generates a display name for a file based on its type and module.
     * Similar to the logic in AndroidViewBuildAndReadmeProvider and CustomNonAndroidNodeProvider.
     */
    private fun generateDisplayName(file: VirtualFile, module: Module): String? {
        val gradleIdentityPath = module.getGradleIdentityPath()
        val gradleProjectPath = module.getGradleProjectPath()?.toHolder()

        // Determine the project display name with appropriate prefix
        val projectDisplayName = when {
            gradleIdentityPath == ":" -> PROJECT_PREFIX + module.name
            gradleProjectPath?.path == ":" -> BUILD_PREFIX + gradleIdentityPath
            else -> MODULE_PREFIX + (gradleIdentityPath ?: module.name)
        }

        // When files are shown in top-level group, always show the full prefix
        // to identify which module they belong to
        return projectDisplayName
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
