package com.z8dn.plugins.a2pt.utils

import com.intellij.icons.AllIcons
import com.intellij.openapi.fileTypes.FileTypeManager
import javax.swing.Icon

/**
 * Single place that decides which icon a file group shows: the catalog icon for its
 * `iconKey`, or the pattern-derived one when the key is null or no longer in the catalog.
 */
object GroupIconResolver {

    fun resolve(iconKey: String?, autoDetect: () -> Icon): Icon =
        GroupIconCatalog.find(iconKey)?.icon ?: autoDetect()

    fun resolve(iconKey: String?, patterns: List<String>): Icon =
        resolve(iconKey) { autoDetectFromPatterns(patterns) }

    /**
     * - If there's only one non-exclusion pattern, use a file-type-specific icon
     * - Otherwise, use a generic folder icon
     */
    fun autoDetectFromPatterns(patterns: List<String>): Icon {
        val inclusionPatterns = patterns.filter { !it.startsWith("!") }
        if (inclusionPatterns.size == 1) {
            val pattern = inclusionPatterns[0]
            val fileTypeManager = FileTypeManager.getInstance()

            // Handle wildcard patterns like "*.md"
            if (pattern.startsWith("*.")) {
                val extension = pattern.substring(2)
                val fileType = fileTypeManager.getFileTypeByExtension(extension)
                return fileType.icon ?: AllIcons.FileTypes.Text
            }

            // Handle exact filename patterns like "LICENSE"
            if (!pattern.contains("*") && !pattern.contains("/")) {
                val fileType = fileTypeManager.getFileTypeByFileName(pattern)
                return fileType.icon ?: AllIcons.FileTypes.Text
            }
        }

        // Default to folder icon for multiple patterns or complex wildcards
        return AllIcons.Nodes.Folder
    }
}
