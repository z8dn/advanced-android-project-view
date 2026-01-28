package com.z8dn.plugins.a2pt

import com.android.tools.idea.navigator.AndroidProjectViewPane
import com.intellij.ide.projectView.TreeStructureProvider
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.project.Project

/**
 * Tree structure provider that adds a top-level custom files grouping to the Android Project View.
 * This node appears at the same level as "Gradle Scripts" and modules.
 */
class ProjectFilesTreeStructureProvider : TreeStructureProvider {

    override fun modify(
        parent: AbstractTreeNode<*>,
        children: MutableCollection<AbstractTreeNode<*>>,
        settings: ViewSettings?
    ): Collection<AbstractTreeNode<*>> {
        // Only apply to Android Project View root
        if (settings == null || parent.value !is Project) {
            return children
        }

        // Check if this is the Android Project View
        val project = parent.value as? Project ?: return children

        val androidViewSettings = AndroidViewSettings.getInstance()

        val modifiedChildren = children.toMutableList()

        // Add a group node for each custom file group if not showing in modules
        if (!AndroidViewNodeUtils.showProjectFilesInModule()) {
            for (group in androidViewSettings.projectFileGroups) {
                if (group.patterns.isNotEmpty()) {
                    modifiedChildren.add(ProjectFilesGroupNode(project, settings, group))
                }
            }
        }

        return modifiedChildren
    }
}
