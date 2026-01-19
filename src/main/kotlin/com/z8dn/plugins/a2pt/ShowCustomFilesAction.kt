package com.z8dn.plugins.a2pt

import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.ProjectManager

class ShowCustomFilesAction : ToggleAction(
    { AndroidViewBundle.message("action.ShowCustomFilesAction.text") }
) {
    private val settings = AndroidViewSettings.getInstance()

    override fun isSelected(e: AnActionEvent): Boolean {
        return settings.showCustomFiles
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        if (settings.showCustomFiles != state) {
            settings.showCustomFiles = state

            // Refresh all open projects to reflect the change
            ProjectManager.getInstance().openProjects
                .filter { !it.isDisposed }
                .forEach { project ->
                    ProjectView.getInstance(project).currentProjectViewPane?.updateFromRoot(true)
                }
        }
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}
