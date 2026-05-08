package com.z8dn.plugins.a2pt.utils

import com.intellij.icons.AllIcons
import javax.swing.Icon

data class GroupIconEntry(
    val key: String,
    val displayName: String,
    val icon: Icon
)

object GroupIconCatalog {

    val entries: List<GroupIconEntry> by lazy { buildEntries() }

    fun find(key: String?): GroupIconEntry? =
        key?.let { k -> entries.firstOrNull { it.key == k } }

    private fun buildEntries(): List<GroupIconEntry> {
        val candidates: List<Triple<String, String, () -> Icon>> = listOf(
            Triple("nodes.module",              "Module",          { AllIcons.Nodes.Module }),
            Triple("nodes.package",             "Package",         { AllIcons.Nodes.Package }),
            Triple("nodes.folder",              "Folder",          { AllIcons.Nodes.Folder }),
            Triple("nodes.configFolder",        "Config Folder",   { AllIcons.Nodes.ConfigFolder }),
            Triple("nodes.dataSchema",          "Database",        { AllIcons.Nodes.DataSchema }),
            Triple("nodes.dataTables",          "Tables",          { AllIcons.Nodes.DataTables }),
            Triple("nodes.plugin",              "Plugin",          { AllIcons.Nodes.Plugin }),
            Triple("nodes.test",                "Test",            { AllIcons.Nodes.JunitTestMark }),
            Triple("nodes.console",             "Console",         { AllIcons.Nodes.Console }),
            Triple("nodes.bookmark",            "Bookmark",        { AllIcons.Nodes.Bookmark }),
            Triple("general.settings",          "Settings",        { AllIcons.General.Settings }),
            Triple("general.gearPlain",         "Gear",            { AllIcons.General.GearPlain }),
            Triple("general.web",               "Web",             { AllIcons.General.Web }),
            Triple("general.note",              "Note",            { AllIcons.General.Note }),
            Triple("general.information",       "Info",            { AllIcons.General.Information }),
            Triple("general.warning",           "Warning",         { AllIcons.General.Warning }),
            Triple("general.error",             "Error",           { AllIcons.General.Error }),
            Triple("general.user",              "User",            { AllIcons.General.User }),
            Triple("general.locate",            "Locate",          { AllIcons.General.Locate }),
            Triple("general.contextHelp",       "Help",            { AllIcons.General.ContextHelp }),
            Triple("actions.startDebugger",     "Debug",           { AllIcons.Actions.StartDebugger }),
            Triple("actions.execute",           "Run",             { AllIcons.Actions.Execute }),
            Triple("actions.lightning",         "Lightning",       { AllIcons.Actions.Lightning }),
            Triple("actions.find",              "Find",            { AllIcons.Actions.Find }),
            Triple("actions.upload",            "Upload",          { AllIcons.Actions.Upload }),
            Triple("actions.download",          "Download",        { AllIcons.Actions.Download }),
            Triple("fileTypes.archive",         "Archive",         { AllIcons.FileTypes.Archive }),
            Triple("fileTypes.config",          "Config",          { AllIcons.FileTypes.Config }),
            Triple("fileTypes.json",            "JSON",            { AllIcons.FileTypes.Json }),
            Triple("fileTypes.xml",             "XML",             { AllIcons.FileTypes.Xml }),
            Triple("fileTypes.yaml",            "YAML",            { AllIcons.FileTypes.Yaml }),
            Triple("fileTypes.text",            "Text",            { AllIcons.FileTypes.Text }),
            Triple("toolwindows.documentation", "Documentation",   { AllIcons.Toolwindows.Documentation }),
            Triple("toolwindows.problems",      "Problems",        { AllIcons.Toolwindows.Problems }),
            Triple("toolwindows.changes",       "Version Control", { AllIcons.Toolwindows.ToolWindowChanges }),
            Triple("vcs.branch",                "Branch",          { AllIcons.Vcs.Branch }),
            Triple("debugger.console",          "Debug Console",   { AllIcons.Debugger.Console })
        )

        return candidates.mapNotNull { (key, displayName, supplier) ->
            runCatching { GroupIconEntry(key, displayName, supplier()) }.getOrNull()
        }
    }
}
