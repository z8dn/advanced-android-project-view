package com.z8dn.plugins.a2pt.utils

import com.intellij.icons.AllIcons
import com.intellij.openapi.fileTypes.FileTypeManager
import javax.swing.Icon

object GroupIconResolver {
    fun resolve(iconKey: String?, autoDetect: () -> Icon): Icon =
        GroupIconCatalog.find(iconKey)?.icon ?: autoDetect()

    fun resolve(iconKey: String?, patterns: List<String>): Icon =
        resolve(iconKey) { autoDetectFromPatterns(patterns) }

    fun autoDetectFromPatterns(patterns: List<String>): Icon {
        val inclusionPatterns = patterns.filter { !it.startsWith("!") }
        if (inclusionPatterns.size == 1) {
            val pattern = inclusionPatterns[0]
            val fileTypeManager = FileTypeManager.getInstance()
            if (pattern.startsWith("*.")) {
                val extension = pattern.substring(2)
                return fileTypeManager.getFileTypeByExtension(extension).icon ?: AllIcons.FileTypes.Text
            }
            if (!pattern.contains("*") && !pattern.contains("/")) {
                return fileTypeManager.getFileTypeByFileName(pattern).icon ?: AllIcons.FileTypes.Text
            }
        }
        return AllIcons.Nodes.Folder
    }
}
