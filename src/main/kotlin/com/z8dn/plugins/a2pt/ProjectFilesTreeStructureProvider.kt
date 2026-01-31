package com.z8dn.plugins.a2pt

import com.android.tools.idea.navigator.nodes.AndroidViewProjectNode
import com.intellij.ide.projectView.TreeStructureProvider
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.util.treeView.AbstractTreeNode

/**
 * TreeStructureProvider that adds a ProjectFileGroupNode at the project root level
 * when showProjectFilesInModule is false.
 *
 * This provider intercepts the AndroidViewProjectNode (the root of the Android Project View)
 * and adds a ProjectFileGroupNode as a top-level child to group all project files together.
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

        // Only add the group node if showProjectFilesInModule is false
        if (AndroidViewNodeUtils.showProjectFilesInModule()) {
            return children
        }

        val project = parent.project ?: return children
        val modified = ArrayList(children)

        // Add ProjectFileGroupNode as a top-level node
        val projectFileGroupNode = ProjectFileGroupNode(project, settings ?: parent.settings)

        // Only add if there are actually project files to show
        if (projectFileGroupNode.children.isNotEmpty()) {
            modified.add(projectFileGroupNode)
        }

        return modified
    }
}
