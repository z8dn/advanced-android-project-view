package com.z8dn.plugins.a2pt

import com.android.tools.idea.navigator.AndroidProjectViewPane
import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/**
 * Startup activity that refreshes the Android Project View when a project opens.
 * This ensures that project file groups are displayed correctly on initial load.
 */
class AndroidViewStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        // Use invokeLater to ensure the UI is ready
        com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) {
                val projectView = ProjectView.getInstance(project)
                val currentPane = projectView.currentProjectViewPane

                // Only refresh if we're in Android Project View
                if (currentPane is AndroidProjectViewPane) {
                    currentPane.updateFromRoot(true)
                }
            }
        }
    }
}
