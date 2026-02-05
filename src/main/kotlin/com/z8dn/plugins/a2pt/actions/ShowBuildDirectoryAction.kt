package com.z8dn.plugins.a2pt.actions

import com.z8dn.plugins.a2pt.AndroidViewBundle
import com.z8dn.plugins.a2pt.settings.AndroidViewSettings

import com.android.tools.idea.navigator.ANDROID_VIEW_ID
import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.ProjectManager

class ShowBuildDirectoryAction : ToggleAction(
    { AndroidViewBundle.message("action.ProjectView.ShowBuildDirectoryAction.text") }
), AndroidViewAction {
    private val settings = AndroidViewSettings.getInstance()

    override fun isSelected(e: AnActionEvent): Boolean {
        return settings.showBuildDirectory
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        if (settings.showBuildDirectory != state) {
            settings.showBuildDirectory = state

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

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}
