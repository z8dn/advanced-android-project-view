package com.z8dn.plugins.a2pt

import com.android.tools.idea.navigator.AndroidProjectViewPane
import com.intellij.ide.projectView.TreeStructureProvider
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.project.Project

/**
 * Tree structure provider that adds a top-level "Custom Nodes" grouping to the Android Project View.
 * This node appears at the same level as "Gradle Scripts" and modules.
 */
class CustomNodesTreeStructureProvider : TreeStructureProvider {

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

        // Only show groupings if custom files are enabled and grouping is enabled
        if (!androidViewSettings.showCustomFiles || !androidViewSettings.groupCustomNodes) {
            return children
        }

        val modifiedChildren = children.toMutableList()
        val modules = com.intellij.openapi.module.ModuleManager.getInstance(project).modules

        // Add user-defined groupings as top-level nodes
        if (androidViewSettings.customGroupings.isNotEmpty()) {
            for (grouping in androidViewSettings.customGroupings) {
                if (grouping.patterns.isEmpty()) continue

                val groupingFiles = mutableListOf<Pair<com.intellij.openapi.module.Module, com.intellij.openapi.vfs.VirtualFile>>()

                // Collect files matching this grouping's patterns from all modules
                for (module in modules) {
                    if (module.isDisposed) continue

                    val matchingFiles = AndroidViewNodeUtils.findMatchingFiles(module, grouping.patterns)
                    for (file in matchingFiles) {
                        groupingFiles.add(module to file)
                    }
                }

                // Only create grouping node if it has files
                if (groupingFiles.isNotEmpty()) {
                    modifiedChildren.add(NamedGroupingNode(project, grouping.name, groupingFiles, settings))
                }
            }
        } else if (androidViewSettings.filePatterns.isNotEmpty()) {
            // Fallback: create a single "Custom Nodes" grouping for legacy filePatterns
            modifiedChildren.add(CustomNodesGroupNode(project, null, settings, null, emptyList()))
        }

        return modifiedChildren
    }
}
