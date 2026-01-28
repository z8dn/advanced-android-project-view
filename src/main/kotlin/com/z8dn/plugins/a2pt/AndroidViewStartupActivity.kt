package com.z8dn.plugins.a2pt

import com.android.tools.idea.navigator.AndroidProjectViewPane
import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/**
 * Startup activity that refreshes the Android Project View when a project is opened.
 *
 * This ensures that project files and other custom nodes are displayed correctly
 * when the IDE starts, without requiring the user to toggle view settings.
 */
class AndroidViewStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) {
                val projectView = ProjectView.getInstance(project)
                val currentPane = projectView.currentProjectViewPane

                if (currentPane is AndroidProjectViewPane) {
                    currentPane.updateFromRoot(true)
                }
            }
        }
    }
}
