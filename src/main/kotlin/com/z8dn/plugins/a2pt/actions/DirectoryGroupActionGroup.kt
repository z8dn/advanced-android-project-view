package com.z8dn.plugins.a2pt.actions

import com.z8dn.plugins.a2pt.AndroidViewBundle
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

class IncludeDirectoryInGroupActionGroup : DefaultActionGroup() {
    init {
        templatePresentation.setText { AndroidViewBundle.message("action.ProjectView.IncludeDirectoryInGroup.text") }
    }

    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        val event = e ?: return EMPTY_ARRAY
        val project = event.project ?: return EMPTY_ARRAY
        val dir = event.getData(CommonDataKeys.VIRTUAL_FILE)
            ?.takeIf { it.isDirectory } ?: return EMPTY_ARRAY

        val groups = AndroidViewSettings.getInstance().projectFileGroups
        val actions = mutableListOf<AnAction>()

        for (group in groups) {
            actions.add(AddToGroupAction(project, dir, group))
        }

        if (groups.isNotEmpty()) actions.add(Separator.getInstance())
        actions.add(CreateNewGroupAction(project, dir))

        return actions.toTypedArray()
    }

    override fun update(e: AnActionEvent) {
        val dir = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabledAndVisible = dir?.isDirectory == true
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    private class AddToGroupAction(
        private val project: Project,
        private val dir: VirtualFile,
        private val group: ProjectFileGroup
    ) : AnAction(group.groupName) {

        override fun actionPerformed(e: AnActionEvent) {
            val pattern = buildPattern(project, dir) ?: return
            group.patterns.add(pattern)
            refreshAllProjects()
        }

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
    }

    private class CreateNewGroupAction(
        private val project: Project,
        private val dir: VirtualFile
    ) : AnAction({ AndroidViewBundle.message("action.CreateNewGroup.text") }) {

        override fun actionPerformed(e: AnActionEvent) {
            val pattern = buildPattern(project, dir) ?: return
            val prefilledGroup = ProjectFileGroup(dir.name, mutableListOf(pattern))
            // Here settings *are* the live state — there is no draft table in between.
            val siblings = AndroidViewSettings.getInstance().projectFileGroups.toList()
            val dialog = ProjectFileGroupDialog(project, prefilledGroup, siblings)
            if (dialog.showAndGet()) {
                AndroidViewSettings.getInstance().projectFileGroups.add(dialog.getProjectFileGroup())
                refreshAllProjects()
            }
        }

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
    }
}

private fun buildPattern(project: Project, dir: VirtualFile): String? {
    val base = project.basePath
        ?.let { LocalFileSystem.getInstance().findFileByPath(it) } ?: return null
    val relPath = VfsUtil.getRelativePath(dir, base, '/') ?: return null
    return if (relPath.isEmpty()) "**" else "$relPath/**"
}

private fun refreshAllProjects() {
    ProjectManager.getInstance().openProjects
        .filter { !it.isDisposed }
        .forEach { ProjectView.getInstance(it)?.refresh() }
}
