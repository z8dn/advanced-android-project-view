package com.z8dn.plugins.a2pt

import com.android.tools.idea.navigator.nodes.AndroidViewProjectNode
import com.intellij.ide.projectView.TreeStructureProvider
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.util.treeView.AbstractTreeNode

/**
 * TreeStructureProvider that adds ProjectFileGroupNode(s) at the project root level
 * when showProjectFilesInModule is false.
 *
 * This provider intercepts the AndroidViewProjectNode (the root of the Android Project View)
 * and adds a ProjectFileGroupNode for each configured ProjectFileGroup to group project files.
 */
class ProjectFilesTreeStructureProvider : TreeStructureProvider {

    override fun modify(
        parent: AbstractTreeNode<*>,
        children: Collection<AbstractTreeNode<*>>,
        settings: ViewSettings?
    ): Collection<AbstractTreeNode<*>> {
        // Only modify the root project node
        if (parent !is AndroidViewProjectNode) {
            return children
        }

        // Only add group nodes if showProjectFilesInModule is false
        if (AndroidViewNodeUtils.showProjectFilesInModule()) {
            return children
        }

        val project = parent.project ?: return children
        val modified = ArrayList(children)

        // Get configured project file groups from settings
        val projectFileGroups = AndroidViewSettings.getInstance().projectFileGroups

        // Add a ProjectFileGroupNode for each configured group
        for (fileGroup in projectFileGroups) {
            val groupNode = ProjectFileGroupNode(project, settings ?: parent.settings, fileGroup)

            // Only add if there are actually project files to show in this group
            if (groupNode.children.isNotEmpty()) {
                modified.add(groupNode)
            }
        }

        return modified
    }
}
