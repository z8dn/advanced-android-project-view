package com.z8dn.plugins.a2pt

import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction

/**
 * Toggle action to control whether custom nodes are grouped in a top-level section
 * or displayed directly in their respective modules.
 */
class GroupCustomNodesAction : ToggleAction(
    { AndroidViewBundle.message("action.GroupCustomNodesAction.text") }
) {

    override fun isSelected(e: AnActionEvent): Boolean {
        return AndroidViewSettings.getInstance().groupCustomNodes
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        val settings = AndroidViewSettings.getInstance()
        settings.groupCustomNodes = state

        // Refresh the project view to reflect the changes
        val project = e.project
        if (project != null && !project.isDisposed) {
            ProjectView.getInstance(project).currentProjectViewPane?.updateFromRoot(true)
        }
    }

    override fun update(e: AnActionEvent) {
        super.update(e)
        // Enable the action only when custom files display is enabled
        val settings = AndroidViewSettings.getInstance()
        e.presentation.isEnabled = settings.showCustomFiles && settings.filePatterns.isNotEmpty()
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}
