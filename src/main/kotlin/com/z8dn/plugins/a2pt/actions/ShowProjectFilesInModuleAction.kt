package com.z8dn.plugins.a2pt.actions

import com.z8dn.plugins.a2pt.AndroidViewBundle
import com.z8dn.plugins.a2pt.settings.AndroidViewSettings

import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.ProjectManager

class ShowProjectFilesInModuleAction : ToggleAction(
    { AndroidViewBundle.message("action.ProjectView.ShowProjectFilesInModuleAction.text") }
), AndroidViewAction {
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
                    ProjectView.getInstance(project)?.refresh()
                }
        }
    }

    override fun update(e: AnActionEvent) {
        super.update(e)
        updateAndroidViewVisibility(e)
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}
