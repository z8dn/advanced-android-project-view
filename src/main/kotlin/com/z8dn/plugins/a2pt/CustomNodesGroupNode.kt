package com.z8dn.plugins.a2pt

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode
import com.intellij.ide.projectView.impl.nodes.PsiFileNode
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager

/**
 * A top-level grouping node that consolidates all custom files
 * from all modules under a single "Custom Nodes" section in the Android Project View.
 * This node appears at the same level as "Gradle Scripts" and other top-level nodes.
 *
 * @param project The project
 * @param settings View settings for rendering nodes
 */
class CustomNodesGroupNode(
    project: Project,
    @Suppress("UNUSED_PARAMETER") module: Module?, // Keep for backward compatibility
    private val settings: ViewSettings,
    @Suppress("UNUSED_PARAMETER") buildDir: VirtualFile? = null, // Keep for backward compatibility
    @Suppress("UNUSED_PARAMETER") customFiles: List<VirtualFile> = emptyList() // Keep for backward compatibility
) : ProjectViewNode<String>(project, "Custom Nodes", settings) {

    override fun getChildren(): Collection<AbstractTreeNode<*>> {
        val project = myProject ?: return emptyList()
        val children = mutableListOf<AbstractTreeNode<*>>()
        val androidViewSettings = AndroidViewSettings.getInstance()

        // Only add custom files if enabled
        if (!androidViewSettings.showCustomFiles) {
            return emptyList()
        }

        val modules = ModuleManager.getInstance(project).modules
        val psiManager = PsiManager.getInstance(project)

        // If there are custom groupings defined, create a node for each grouping
        if (androidViewSettings.customGroupings.isNotEmpty()) {
            for (grouping in androidViewSettings.customGroupings) {
                if (grouping.patterns.isEmpty()) continue

                val groupingFiles = mutableListOf<Pair<Module, VirtualFile>>()

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
                    children.add(NamedGroupingNode(project, grouping.name, groupingFiles, settings))
                }
            }
        } else if (androidViewSettings.filePatterns.isNotEmpty()) {
            // Fallback: use legacy filePatterns as a single unnamed group
            for (module in modules) {
                if (module.isDisposed) continue

                val matchingFiles = AndroidViewNodeUtils.findMatchingFiles(module, androidViewSettings.filePatterns)
                for (file in matchingFiles) {
                    val psiFile = psiManager.findFile(file)
                    if (psiFile != null) {
                        children.add(CustomFileNodeWithModule(project, psiFile, module, settings))
                    }
                }
            }
        }

        return children
    }

    override fun update(presentation: PresentationData) {
        presentation.presentableText = "Custom Nodes"
    }

    override fun contains(file: VirtualFile): Boolean = false
}
