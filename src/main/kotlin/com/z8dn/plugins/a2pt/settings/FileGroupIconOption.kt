package com.z8dn.plugins.a2pt.settings

import com.intellij.icons.AllIcons
import javax.swing.Icon

/**
 * Enumeration of available icon options for file groups.
 * Each option provides a display name and the corresponding icon.
 */
enum class FileGroupIconOption(
    val displayName: String,
    val iconPath: String?,
    val icon: Icon
) {
    AUTO("Auto (based on patterns)", null, AllIcons.FileTypes.Any_type),
    FOLDER("Folder", "AllIcons.Nodes.Folder", AllIcons.Nodes.Folder),
    PACKAGE("Package", "AllIcons.Nodes.Package", AllIcons.Nodes.Package),
    MODULE("Module", "AllIcons.Nodes.Module", AllIcons.Nodes.Module),
    CONFIG_FOLDER("Config Folder", "AllIcons.Nodes.ConfigFolder", AllIcons.Nodes.ConfigFolder),
    DATA_TABLES("Data Tables", "AllIcons.Nodes.DataTables", AllIcons.Nodes.DataTables),
    RESOURCES("Resources", "AllIcons.Nodes.ResourceBundle", AllIcons.Nodes.ResourceBundle),
    WEB_FOLDER("Web Folder", "AllIcons.Nodes.WebFolder", AllIcons.Nodes.WebFolder),
    SETTINGS("Settings", "AllIcons.General.Settings", AllIcons.General.Settings),
    GEAR("Gear", "AllIcons.General.GearPlain", AllIcons.General.GearPlain),
    LIST_FILES("List Files", "AllIcons.Actions.ListFiles", AllIcons.Actions.ListFiles),
    GROUP_BY("Group By", "AllIcons.Actions.GroupBy", AllIcons.Actions.GroupBy),
    COPY("Copy", "AllIcons.Actions.Copy", AllIcons.Actions.Copy),
    EDIT("Edit", "AllIcons.Actions.Edit", AllIcons.Actions.Edit),
    CONFIG("Config File", "AllIcons.FileTypes.Config", AllIcons.FileTypes.Config),
    TEXT("Text File", "AllIcons.FileTypes.Text", AllIcons.FileTypes.Text),
    PROPERTIES("Properties", "AllIcons.FileTypes.Properties", AllIcons.FileTypes.Properties),
    ARCHIVE("Archive", "AllIcons.FileTypes.Archive", AllIcons.FileTypes.Archive);

    companion object {
        /**
         * Finds the IconOption that matches the given icon path.
         * Returns AUTO if no match is found.
         */
        fun fromIconPath(iconPath: String?): FileGroupIconOption {
            if (iconPath == null) return AUTO
            return entries.find { it.iconPath == iconPath } ?: AUTO
        }
    }
}
