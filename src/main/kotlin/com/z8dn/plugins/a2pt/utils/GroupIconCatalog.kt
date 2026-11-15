package com.z8dn.plugins.a2pt.utils

import com.intellij.icons.AllIcons
import com.z8dn.plugins.a2pt.AndroidViewBundle
import javax.swing.Icon

/**
 * One selectable group icon. [key] is what gets persisted in
 * [com.z8dn.plugins.a2pt.settings.ProjectFileGroup.iconKey], so it must stay stable even if the
 * underlying [AllIcons] field is renamed.
 */
data class GroupIconEntry(
    val key: String,
    val icon: Icon
) {
    /** Resolved on demand so the catalog itself stays free of the message bundle. */
    val displayName: String
        get() = AndroidViewBundle.message(displayNameKey(key))

    companion object {
        /** The message bundle key holding the display name for a catalog [key]. */
        fun displayNameKey(key: String): String = "icon.$key"
    }
}

object GroupIconCatalog {

    val entries: List<GroupIconEntry> by lazy {
        listOf(
            GroupIconEntry("nodes.module", AllIcons.Nodes.Module),
            GroupIconEntry("nodes.package", AllIcons.Nodes.Package),
            GroupIconEntry("nodes.folder", AllIcons.Nodes.Folder),
            GroupIconEntry("nodes.configFolder", AllIcons.Nodes.ConfigFolder),
            GroupIconEntry("nodes.dataSchema", AllIcons.Nodes.DataSchema),
            GroupIconEntry("nodes.dataTables", AllIcons.Nodes.DataTables),
            GroupIconEntry("nodes.plugin", AllIcons.Nodes.Plugin),
            GroupIconEntry("nodes.test", AllIcons.Nodes.JunitTestMark),
            GroupIconEntry("nodes.console", AllIcons.Nodes.Console),
            GroupIconEntry("nodes.bookmark", AllIcons.Nodes.Bookmark),
            GroupIconEntry("general.settings", AllIcons.General.Settings),
            GroupIconEntry("general.gearPlain", AllIcons.General.GearPlain),
            GroupIconEntry("general.web", AllIcons.General.Web),
            GroupIconEntry("general.note", AllIcons.General.Note),
            GroupIconEntry("general.information", AllIcons.General.Information),
            GroupIconEntry("general.warning", AllIcons.General.Warning),
            GroupIconEntry("general.error", AllIcons.General.Error),
            GroupIconEntry("general.user", AllIcons.General.User),
            GroupIconEntry("general.locate", AllIcons.General.Locate),
            GroupIconEntry("general.contextHelp", AllIcons.General.ContextHelp),
            GroupIconEntry("actions.startDebugger", AllIcons.Actions.StartDebugger),
            GroupIconEntry("actions.execute", AllIcons.Actions.Execute),
            GroupIconEntry("actions.lightning", AllIcons.Actions.Lightning),
            GroupIconEntry("actions.find", AllIcons.Actions.Find),
            GroupIconEntry("actions.upload", AllIcons.Actions.Upload),
            GroupIconEntry("actions.download", AllIcons.Actions.Download),
            GroupIconEntry("fileTypes.archive", AllIcons.FileTypes.Archive),
            GroupIconEntry("fileTypes.config", AllIcons.FileTypes.Config),
            GroupIconEntry("fileTypes.json", AllIcons.FileTypes.Json),
            GroupIconEntry("fileTypes.xml", AllIcons.FileTypes.Xml),
            GroupIconEntry("fileTypes.yaml", AllIcons.FileTypes.Yaml),
            GroupIconEntry("fileTypes.text", AllIcons.FileTypes.Text),
            GroupIconEntry("toolwindows.documentation", AllIcons.Toolwindows.Documentation),
            GroupIconEntry("toolwindows.problems", AllIcons.Toolwindows.Problems),
            GroupIconEntry("toolwindows.changes", AllIcons.Toolwindows.ToolWindowChanges),
            GroupIconEntry("vcs.branch", AllIcons.Vcs.Branch),
            GroupIconEntry("debugger.console", AllIcons.Debugger.Console)
        )
    }

    fun find(key: String?): GroupIconEntry? =
        key?.let { k -> entries.firstOrNull { it.key == k } }
}
