package com.z8dn.plugins.a2pt

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.nodes.PsiFileNode
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.ui.SimpleTextAttributes

/**
 * Node representing a project file in the Android Project View.
 *
 * This node displays project files with appropriate styling and ordering.
 */
class ProjectFileNode(
    project: Project,
    value: PsiFile,
    settings: ViewSettings,
    private val qualifier: String? = null,
    private val order: Int = 0
) : PsiFileNode(project, value, settings) {

    override fun update(data: PresentationData) {
        super.update(data)

        val psiFile = value
        if (psiFile?.isValid == true) {
            // Set file name as the main text
            val fileName = if (data.presentableText.isNullOrEmpty()) {
                psiFile.name
            } else {
                data.presentableText
            }

            // Build complete presentation with qualifier
            if (qualifier != null) {
                data.clearText()
                data.addText(fileName, SimpleTextAttributes.REGULAR_ATTRIBUTES)
                data.addText(" ($qualifier)", SimpleTextAttributes.GRAY_ATTRIBUTES)
            } else {
                data.presentableText = fileName
            }
        }
    }

    override fun getSortKey(): Comparable<*> = order
}
