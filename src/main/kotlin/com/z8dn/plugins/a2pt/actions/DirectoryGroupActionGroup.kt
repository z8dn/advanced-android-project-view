package com.z8dn.plugins.a2pt.actions

import com.z8dn.plugins.a2pt.settings.AndroidViewSettings
import com.z8dn.plugins.a2pt.settings.ProjectFileGroup
import com.z8dn.plugins.a2pt.settings.ProjectFileGroupDialog

import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VfsUtil

abstract class DirectoryGroupActionGroup(private val isExclusion: Boolean) : DefaultActionGroup() {

    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        val event = e ?: return EMPTY_ARRAY
        val project = event.project ?: return EMPTY_ARRAY
        val dir = event.getData(CommonDataKeys.VIRTUAL_FILE)
            ?.takeIf { it.isDirectory } ?: return EMPTY_ARRAY

        val groups = AndroidViewSettings.getInstance().projectFileGroups
        val actions = mutableListOf<AnAction>()

        for (group in groups) {
            actions.add(ApplyPatternAction(project, dir, group, isExclusion))
        }

        if (!isExclusion) {
            if (groups.isNotEmpty()) actions.add(Separator.getInstance())
            actions.add(CreateNewGroupAction(project, dir))
        }

        return actions.toTypedArray()
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val dir = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val isAndroidView = project != null &&
            ProjectView.getInstance(project).currentViewId == "AndroidView"
        val isDirectory = dir?.isDirectory == true
        val hasGroups = AndroidViewSettings.getInstance().projectFileGroups.isNotEmpty()

        e.presentation.isEnabledAndVisible = isAndroidView && isDirectory &&
            (!isExclusion || hasGroups)
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    private class ApplyPatternAction(
        private val project: Project,
        private val dir: VirtualFile,
        private val group: ProjectFileGroup,
        private val isExclusion: Boolean
    ) : AnAction(group.groupName) {

        override fun actionPerformed(e: AnActionEvent) {
            val pattern = buildPattern(project, dir, isExclusion) ?: return
            group.patterns.add(pattern)
            refreshAllProjects()
        }

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
    }

    private class CreateNewGroupAction(
        private val project: Project,
        private val dir: VirtualFile
    ) : AnAction("New Group...") {

        override fun actionPerformed(e: AnActionEvent) {
            val pattern = buildPattern(project, dir, false) ?: return
            val prefilledGroup = ProjectFileGroup(dir.name, mutableListOf(pattern))
            val dialog = ProjectFileGroupDialog(prefilledGroup)
            if (dialog.showAndGet()) {
                AndroidViewSettings.getInstance().projectFileGroups.add(dialog.getProjectFileGroup())
                refreshAllProjects()
            }
        }

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
    }
}

private fun buildPattern(project: Project, dir: VirtualFile, isExclusion: Boolean): String? {
    val base = project.basePath
        ?.let { LocalFileSystem.getInstance().findFileByPath(it) } ?: return null
    val relPath = VfsUtil.getRelativePath(dir, base, '/') ?: dir.name
    return if (isExclusion) "!$relPath/**" else "$relPath/**"
}

private fun refreshAllProjects() {
    ProjectManager.getInstance().openProjects
        .filter { !it.isDisposed }
        .forEach { ProjectView.getInstance(it)?.refresh() }
}

class IncludeDirectoryInGroupActionGroup : DirectoryGroupActionGroup(false)
class ExcludeDirectoryFromGroupActionGroup : DirectoryGroupActionGroup(true)
