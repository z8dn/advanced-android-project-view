package com.z8dn.plugins.a2pt.actions

import com.android.tools.idea.navigator.ANDROID_VIEW_ID
import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation

interface AndroidViewAction {
    fun updateAndroidViewVisibility(e: AnActionEvent) {
        val project = e.project
        val isAndroidView = project?.let {
            ProjectView.getInstance(it).currentProjectViewPane?.id == ANDROID_VIEW_ID
        } ?: false
        updatePresentation(e.presentation, isAndroidView)
    }

    fun updatePresentation(presentation: Presentation, isAndroidView: Boolean) {
        presentation.isEnabledAndVisible = isAndroidView
    }
}
