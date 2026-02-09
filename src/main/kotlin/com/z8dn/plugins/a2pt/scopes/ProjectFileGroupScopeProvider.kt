package com.z8dn.plugins.a2pt.scopes

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.SearchScopeProvider
import com.z8dn.plugins.a2pt.AndroidViewBundle
import com.z8dn.plugins.a2pt.settings.AndroidViewSettings

/**
 * Makes project file group scopes available in the ScopeChooserCombo dropdown
 */
class ProjectFileGroupScopeProvider : SearchScopeProvider {

    override fun getDisplayName(): String {
        return AndroidViewBundle.message("scope.ProjectFileGroups.DisplayName")
    }

    override fun getSearchScopes(
        project: Project,
        dataContext: DataContext
    ): List<SearchScope> {
        val result = mutableListOf<SearchScope>()
        val settings = AndroidViewSettings.getInstance()

        // Only add scopes if there are file groups defined
        if (settings.projectFileGroups.isNotEmpty()) {
            // Add child scopes for each file group
            settings.projectFileGroups.forEach { fileGroup ->
                result.add(ProjectFileGroupScope.createFileGroupScope(project, fileGroup))
            }
        }

        return result
    }
}
