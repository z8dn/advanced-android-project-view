package com.z8dn.plugins.a2pt.scopes

import com.intellij.ide.util.treeView.WeighedItem
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.scope.RangeBasedLocalSearchScope
import com.z8dn.plugins.a2pt.AndroidViewBundle
import com.z8dn.plugins.a2pt.settings.AndroidViewSettings
import com.z8dn.plugins.a2pt.settings.ProjectFileGroup
import com.z8dn.plugins.a2pt.utils.ProjectFileDisplayUtils
import java.util.Objects

/**
 * Represents a scope based on project file groups from settings
 */
class ProjectFileGroupScope private constructor(
    private val myProject: Project,
    displayName: String,
    private val fileGroup: ProjectFileGroup?,
    private val isParent: Boolean
) : RangeBasedLocalSearchScope(displayName, false), WeighedItem {

    companion object {
        /**
         * Creates a child scope for a specific project file group
         */
        fun createFileGroupScope(project: Project, fileGroup: ProjectFileGroup): ProjectFileGroupScope {
            return ProjectFileGroupScope(
                project,
                fileGroup.groupName,
                fileGroup,
                false
            )
        }
    }

    private fun matchesFileGroup(file: VirtualFile, fileGroup: ProjectFileGroup): Boolean {
        val relativePath = myProject.basePath
            ?.let { file.path.removePrefix(it).trimStart('/') }
            ?: file.name
        return ProjectFileDisplayUtils.matchesPatterns(file.name, relativePath, fileGroup.patterns)
    }

    private fun matchesAnyFileGroup(file: VirtualFile): Boolean {
        val settings = AndroidViewSettings.getInstance()
        return settings.projectFileGroups.any { group -> matchesFileGroup(file, group) }
    }

    private val myVirtualFiles: Array<VirtualFile> by lazy {
        val allFiles = mutableListOf<VirtualFile>()
        // Collect files from project
        if (fileGroup != null) {
            // Child scope - filter by specific file group
            collectFiles(myProject, allFiles) { file -> matchesFileGroup(file, fileGroup) }
        } else {
            // Parent scope - all project file groups
            collectFiles(myProject, allFiles) { file -> matchesAnyFileGroup(file) }
        }
        allFiles.toTypedArray()
    }

    private fun collectFiles(project: Project, result: MutableList<VirtualFile>, filter: (VirtualFile) -> Boolean) {
        val projectBaseDir = project.basePath?.let { com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath(it) } ?: return
        collectFilesRecursively(projectBaseDir, result, filter)
    }

    private fun collectFilesRecursively(directory: VirtualFile, result: MutableList<VirtualFile>, filter: (VirtualFile) -> Boolean) {
        directory.children?.forEach { file ->
            if (file.isDirectory) {
                collectFilesRecursively(file, result, filter)
            } else if (filter(file)) {
                result.add(file)
            }
        }
    }

    override fun getVirtualFiles(): Array<VirtualFile> = myVirtualFiles

    override fun getPsiElements(): Array<PsiElement> {
        val elements = mutableListOf<PsiElement>()
        val psiManager = PsiManager.getInstance(myProject)
        myVirtualFiles.forEach { virtualFile ->
            psiManager.findFile(virtualFile)?.let { elements.add(it) }
        }
        return elements.toTypedArray()
    }

    override fun toString(): String {
        val string = StringBuilder()
            .append(AndroidViewBundle.message("scope.ProjectFileGroupScope.Prefix"))
            .append(" ")
            .append(displayName)
        if (isParent) {
            string.append("; ALL") // NON-NLS
        }
        return string.toString()
    }

    override fun getWeight(): Int {
        return if (isParent) 0 else 1
    }

    override fun calcHashCode(): Int {
        return Objects.hash(myProject, fileGroup?.groupName, isParent)
    }

    override fun containsRange(file: PsiFile, range: TextRange): Boolean {
        // For a simple file-based scope, if the file is included, all ranges are included
        return myVirtualFiles.contains(file.virtualFile)
    }

    override fun getRanges(file: VirtualFile): Array<TextRange> {
        // Return empty array for files not in scope, or full file range for files in scope
        return if (myVirtualFiles.contains(file)) {
            emptyArray()
        } else {
            TextRange.EMPTY_ARRAY
        }
    }
}
