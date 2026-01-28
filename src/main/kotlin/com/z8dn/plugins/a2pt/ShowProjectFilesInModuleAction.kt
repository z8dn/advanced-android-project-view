package com.z8dn.plugins.a2pt

import com.android.tools.idea.navigator.AndroidProjectViewPane
import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.ProjectManager

class ShowProjectFilesInModuleAction : ToggleAction(
    { AndroidViewBundle.message("action.ShowProjectFilesInModuleAction.text") }
) {
    private val settings = AndroidViewSettings.getInstance()

    override fun isSelected(e: AnActionEvent): Boolean {
        return settings.showProjectFilesInModule
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        if (settings.showProjectFilesInModule != state) {
            settings.showProjectFilesInModule = state

            // Refresh all open projects to reflect the change
            ProjectManager.getInstance().openProjects
                .filter { !it.isDisposed }
                .forEach { project ->
                    ProjectView.getInstance(project).currentProjectViewPane?.updateFromRoot(false)
                }
        }
    }

    override fun update(e: AnActionEvent) {
        super.update(e)
        // Only show this action when in Android Project View
        val project = e.project
        val isAndroidView = project?.let {
            ProjectView.getInstance(it).currentProjectViewPane is AndroidProjectViewPane
        } ?: false
        e.presentation.isEnabledAndVisible = isAndroidView
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}
