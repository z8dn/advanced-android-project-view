package com.z8dn.plugins.a2pt.actions

import com.intellij.ide.actions.ShowSettingsUtilImpl
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.z8dn.plugins.a2pt.AndroidViewBundle
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class CustomizeAndroidTreeViewAction : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        ShowSettingsUtilImpl.showSettingsDialog(
            e.project,
            "com.z8dn.plugins.a2pt.settings",
            AndroidViewBundle.message("settings.DisplayName.text")
        )
    }
}
