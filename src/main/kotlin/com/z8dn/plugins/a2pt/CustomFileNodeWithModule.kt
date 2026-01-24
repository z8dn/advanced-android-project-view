package com.z8dn.plugins.a2pt

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.nodes.PsiFileNode
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.ui.SimpleTextAttributes
import org.jetbrains.plugins.gradle.util.GradleConstants

/**
 * A custom file node that displays the module type and name beside the file name.
 * Similar to how Gradle Scripts shows module information with prefixes like:
 * - "Project: name" for root project
 * - "Module: name" for regular modules
 * - "Included build: :name" for included builds
 *
 * @param project The project
 * @param psiFile The PSI file to display
 * @param module The module this file belongs to
 * @param settings View settings for rendering nodes
 */
class CustomFileNodeWithModule(
    project: Project,
    psiFile: PsiFile,
    private val module: Module,
    settings: ViewSettings
) : PsiFileNode(project, psiFile, settings) {

    override fun update(presentation: PresentationData) {
        super.update(presentation)

        // Add module name with appropriate prefix, similar to Gradle Scripts
        val fileName = virtualFile?.name ?: value?.name
        if (fileName != null) {
            presentation.clearText()
            presentation.addText(fileName, SimpleTextAttributes.REGULAR_ATTRIBUTES)

            val moduleLabel = getModuleLabel(module)
            presentation.addText(" ($moduleLabel)", SimpleTextAttributes.GRAYED_ATTRIBUTES)
        }
    }

    private fun getModuleLabel(module: Module): String {
        val moduleName = module.name
        val projectName = myProject?.name

        // Convert module name from dot notation to colon notation (e.g., "project.app" -> ":app")
        val colonNotationName = convertToColonNotation(moduleName, projectName)

        // Check if this is the root project module first
        if (moduleName == projectName || moduleName.endsWith(".main") && moduleName.startsWith(projectName ?: "")) {
            return "Project: $projectName"
        }

        // Check if this is an included build using Gradle-specific data
        val externalProjectId = ExternalSystemApiUtil.getExternalProjectId(module)
        if (externalProjectId != null) {
            // Included builds have a different project ID pattern
            // They typically don't start with the main project name
            val isIncludedBuild = projectName != null && !externalProjectId.startsWith(":") && externalProjectId != projectName
            if (isIncludedBuild) {
                return "Included build: $colonNotationName"
            }
        }

        // Alternative check: compare external root project paths
        val moduleRootPath = ExternalSystemApiUtil.getExternalRootProjectPath(module)
        val projectRootPath = myProject?.basePath

        if (moduleRootPath != null && projectRootPath != null) {
            // Normalize paths for comparison
            val normalizedModuleRoot = moduleRootPath.trimEnd('/')
            val normalizedProjectRoot = projectRootPath.trimEnd('/')

            // If paths are different, it's an included build
            if (normalizedModuleRoot != normalizedProjectRoot) {
                return "Included build: $colonNotationName"
            }
        }

        // Otherwise, it's a regular module
        return "Module: $colonNotationName"
    }

    private fun convertToColonNotation(moduleName: String, projectName: String?): String {
        // Remove project prefix if present
        val nameWithoutProject = if (projectName != null && moduleName.startsWith("$projectName.")) {
            moduleName.removePrefix("$projectName.")
        } else {
            moduleName
        }

        // Convert dots to colons and ensure it starts with a colon
        return if (nameWithoutProject.isEmpty()) {
            ":"
        } else {
            ":" + nameWithoutProject.replace(".", ":")
        }
    }
}
