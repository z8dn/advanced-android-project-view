package com.z8dn.plugins.a2pt.settings

import com.z8dn.plugins.a2pt.AndroidViewBundle

import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.table.JBTable
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.*
import javax.swing.table.AbstractTableModel

/**
 * Settings page for Advanced Android Project Tree.
 */
class AndroidViewSettingsConfigurable : SearchableConfigurable {

    private var panel: JPanel? = null
    private var showBuildDirectoryCheckBox: JBCheckBox? = null
    private var groupsTable: JBTable? = null
    private var groupsTableModel: ProjectFileGroupsTableModel? = null

    override fun getId(): String = "com.z8dn.plugins.a2pt.settings"

    override fun getDisplayName(): String = AndroidViewBundle.message("settings.DisplayName.text")

    override fun createComponent(): JComponent {
        val mainPanel = JPanel(GridBagLayout())
        mainPanel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)

        val gbc = GridBagConstraints()
        gbc.gridx = 0
        gbc.gridy = 0
        gbc.anchor = GridBagConstraints.WEST
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.weightx = 1.0
        gbc.insets = Insets(5, 5, 5, 5)

        // Build directory checkbox
        showBuildDirectoryCheckBox = JBCheckBox(AndroidViewBundle.message("settings.ShowBuildDirectory.text")).apply {
            toolTipText = AndroidViewBundle.message("settings.ShowBuildDirectory.description")
        }
        mainPanel.add(showBuildDirectoryCheckBox, gbc)

        // Custom file groups table
        gbc.gridy++
        gbc.insets = Insets(15, 5, 5, 5)
        gbc.fill = GridBagConstraints.BOTH
        gbc.weighty = 1.0
        val tablePanel = createGroupsTablePanel()
        mainPanel.add(tablePanel, gbc)

        panel = mainPanel
        return mainPanel
    }

    private fun createGroupsTablePanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = BorderFactory.createTitledBorder(AndroidViewBundle.message("settings.CustomFileGroups.text"))

        groupsTableModel = ProjectFileGroupsTableModel()
        groupsTable = JBTable(groupsTableModel).apply {
            setShowGrid(true)
            setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        }

        val decorator = ToolbarDecorator.createDecorator(groupsTable!!)
            .setAddAction { addGroup() }
            .setRemoveAction { removeGroup() }
            .setEditAction { editGroup() }
            .addExtraAction(object : AnAction(
                AndroidViewBundle.message("settings.LoadPreset.text"),
                AndroidViewBundle.message("settings.LoadPreset.description"),
                AllIcons.Actions.Download
            ) {
                override fun actionPerformed(e: AnActionEvent) = loadPreset(e.dataContext)
            })

        panel.add(decorator.createPanel(), BorderLayout.CENTER)

        val helpLabel = JBLabel("<html><i>${AndroidViewBundle.message("settings.CustomFileGroups.description")}</i></html>")
        panel.add(helpLabel, BorderLayout.SOUTH)

        return panel
    }

    private fun addGroup() {
        val dialog = ProjectFileGroupDialog()
        if (dialog.showAndGet()) {
            groupsTableModel?.addGroup(dialog.getProjectFileGroup())
        }
    }

    private fun removeGroup() {
        val selectedRow = groupsTable?.selectedRow
        if (selectedRow != null && selectedRow >= 0) {
            groupsTableModel?.removeGroup(selectedRow)
        }
    }

    private fun editGroup() {
        val selectedRow = groupsTable?.selectedRow
        if (selectedRow != null && selectedRow >= 0) {
            val currentGroup = groupsTableModel?.getGroup(selectedRow) ?: return
            val dialog = ProjectFileGroupDialog(currentGroup)
            if (dialog.showAndGet()) {
                groupsTableModel?.updateGroup(selectedRow, dialog.getProjectFileGroup())
            }
        }
    }

    /**
     * Prompts the user to pick a preset, then a merge mode, and applies the result to the table.
     */
    private fun loadPreset(dataContext: DataContext) {
        val presetStep = object : BaseListPopupStep<FileGroupPreset>(
            AndroidViewBundle.message("settings.LoadPreset.ChoosePreset.text"),
            FileGroupPresets.ALL
        ) {
            override fun getTextFor(value: FileGroupPreset): String = value.displayName

            override fun onChosen(selectedValue: FileGroupPreset, finalChoice: Boolean): PopupStep<*>? {
                return doFinalStep { chooseMergeMode(selectedValue, dataContext) }
            }
        }
        JBPopupFactory.getInstance().createListPopup(presetStep).showInBestPositionFor(dataContext)
    }

    private fun chooseMergeMode(preset: FileGroupPreset, dataContext: DataContext) {
        val modes = listOf(
            PresetMergeMode.APPEND_SKIP_DUPLICATES to AndroidViewBundle.message("settings.LoadPreset.Mode.Append.text"),
            PresetMergeMode.OVERWRITE_DUPLICATES to AndroidViewBundle.message("settings.LoadPreset.Mode.Overwrite.text"),
            PresetMergeMode.REPLACE_ALL to AndroidViewBundle.message("settings.LoadPreset.Mode.Replace.text")
        )
        val modeStep = object : BaseListPopupStep<Pair<PresetMergeMode, String>>(
            AndroidViewBundle.message("settings.LoadPreset.ChooseMode.text"),
            modes
        ) {
            override fun getTextFor(value: Pair<PresetMergeMode, String>): String = value.second

            override fun onChosen(selectedValue: Pair<PresetMergeMode, String>, finalChoice: Boolean): PopupStep<*>? {
                return doFinalStep { applyPreset(preset, selectedValue.first) }
            }
        }
        JBPopupFactory.getInstance().createListPopup(modeStep).showInBestPositionFor(dataContext)
    }

    private fun applyPreset(preset: FileGroupPreset, mode: PresetMergeMode) {
        val current = groupsTableModel?.getGroups() ?: emptyList()
        groupsTableModel?.setGroups(FileGroupPresets.merge(current, preset, mode))
    }

    override fun isModified(): Boolean {
        val settings = AndroidViewSettings.getInstance()

        if (showBuildDirectoryCheckBox?.isSelected != settings.showBuildDirectory) {
            return true
        }

        val currentGroups = groupsTableModel?.getGroups() ?: emptyList()
        return currentGroups != settings.projectFileGroups
    }

    override fun apply() {
        val settings = AndroidViewSettings.getInstance()

        settings.showBuildDirectory = showBuildDirectoryCheckBox?.isSelected ?: false

        // Clear and rebuild the groups list to ensure proper change detection
        settings.projectFileGroups.clear()
        groupsTableModel?.getGroups()?.forEach { group ->
            settings.projectFileGroups.add(ProjectFileGroup(group.groupName, group.patterns.toMutableList()))
        }

        // Refresh all open projects to reflect the changes
        // Do this synchronously to ensure settings are applied before refresh
        ProjectManager.getInstance().openProjects
            .filter { !it.isDisposed }
            .forEach { project ->
                ProjectView.getInstance(project).currentProjectViewPane?.updateFromRoot(true)
            }
    }

    override fun reset() {
        val settings = AndroidViewSettings.getInstance()

        showBuildDirectoryCheckBox?.isSelected = settings.showBuildDirectory
        groupsTableModel?.setGroups(settings.projectFileGroups.map {
            ProjectFileGroup(it.groupName, it.patterns.toMutableList())
        })
    }

    /**
     * Table model for managing custom file groups.
     */
    private class ProjectFileGroupsTableModel : AbstractTableModel() {
        private val groups = mutableListOf<ProjectFileGroup>()

        override fun getRowCount(): Int = groups.size

        override fun getColumnCount(): Int = 2

        override fun getColumnName(column: Int): String = when (column) {
            0 -> AndroidViewBundle.message("settings.Table.ColumnName.groupName")
            1 -> AndroidViewBundle.message("settings.Table.ColumnName.patterns")
            else -> ""
        }

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val group = groups[rowIndex]
            return when (columnIndex) {
                0 -> group.groupName
                1 -> group.patterns.joinToString(", ")
                else -> ""
            }
        }

        fun addGroup(group: ProjectFileGroup) {
            groups.add(group)
            fireTableRowsInserted(groups.size - 1, groups.size - 1)
        }

        fun removeGroup(index: Int) {
            groups.removeAt(index)
            fireTableRowsDeleted(index, index)
        }

        fun updateGroup(index: Int, group: ProjectFileGroup) {
            groups[index] = group
            fireTableRowsUpdated(index, index)
        }

        fun getGroup(index: Int): ProjectFileGroup = groups[index]

        fun getGroups(): List<ProjectFileGroup> = groups.toList()

        fun setGroups(newGroups: List<ProjectFileGroup>) {
            groups.clear()
            groups.addAll(newGroups)
            fireTableDataChanged()
        }
    }
}
