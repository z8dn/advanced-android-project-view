package com.z8dn.plugins.a2pt

import com.android.tools.idea.navigator.nodes.AndroidViewNodeProvider
import com.android.tools.idea.navigator.nodes.other.NonAndroidModuleNode
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project

/**
 * Wrapper for Gradle module nodes that adds project file nodes.
 *
 * This class extends [NonAndroidModuleNode] to inject project files into Gradle modules
 * (non-Android modules) when the feature is enabled. Note that build directories are only
 * shown for Android modules, not Gradle modules.
 *
 * @param project The project containing the module
 * @param module The Gradle module to wrap
 * @param settings View settings for rendering nodes
 */
class GradleModuleWithProjectFiles(
    project: Project,
    module: Module,
    settings: ViewSettings
) : NonAndroidModuleNode(project, module, settings) {

    /**
     * Returns the module's children, including project files.
     *
     * @return Collection of child nodes including default children and project file additions
     */
    override fun getModuleChildren(): Collection<AbstractTreeNode<*>> {
        val module = getModule()
        val nodes = ArrayList<AbstractTreeNode<*>>()

        // Start with default children from NonAndroidModuleNode
        nodes.addAll(super.getModuleChildren())

        // Ask all AndroidViewNodeProvider implementations for additional children
        // This mirrors what AndroidModuleNode does
        for (provider in AndroidViewNodeProvider.getProviders()) {
            val providerChildren = provider.getModuleChildren(module, settings)
            if (providerChildren != null) {
                nodes.addAll(providerChildren)
            }
        }

        return nodes
    }

    override fun getSortKey(): Comparable<*> {
        return getModule().name
    }

    override fun getTypeSortKey(): Comparable<*> {
        return sortKey
    }

    private fun getModule(): Module {
        val module = value
        checkNotNull(module)
        return module
    }
}
