package com.z8dn.plugins.a2pt

import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.ProjectManager
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.table.JBTable
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.*
import javax.swing.table.AbstractTableModel

/**
 * Settings page for Android View customization with grouping support.
 */
class AndroidViewSettingsConfigurableNew : SearchableConfigurable {

    private var panel: JPanel? = null
    private var showBuildDirectoryCheckBox: JBCheckBox? = null
    private var showCustomFilesCheckBox: JBCheckBox? = null
    private var groupCustomNodesCheckBox: JBCheckBox? = null
    private var groupingsTable: JBTable? = null
    private var groupingsTableModel: GroupingsTableModel? = null

    override fun getId(): String = "com.z8dn.plugins.a2pt.settings"

    override fun getDisplayName(): String = "Advanced Android Project View"

    override fun createComponent(): JComponent {
        val mainPanel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints()
        gbc.anchor = GridBagConstraints.NORTHWEST
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.insets = Insets(5, 5, 5, 5)

        // Row 0: Build directory checkbox
        gbc.gridx = 0
        gbc.gridy = 0
        gbc.weightx = 1.0
        showBuildDirectoryCheckBox = JBCheckBox(AndroidViewBundle.message("settings.showBuildDirectory")).apply {
            toolTipText = AndroidViewBundle.message("settings.showBuildDirectory.tooltip")
        }
        mainPanel.add(showBuildDirectoryCheckBox, gbc)

        // Row 1: Custom files checkbox
        gbc.gridy = 1
        showCustomFilesCheckBox = JBCheckBox(AndroidViewBundle.message("settings.showCustomFiles")).apply {
            toolTipText = AndroidViewBundle.message("settings.showCustomFiles.tooltip")
            addActionListener {
                updateGroupingsTableState()
                updateGroupCustomNodesState()
            }
        }
        mainPanel.add(showCustomFilesCheckBox, gbc)

        // Row 2: Group custom nodes checkbox
        gbc.gridy = 2
        groupCustomNodesCheckBox = JBCheckBox(AndroidViewBundle.message("settings.groupCustomNodes")).apply {
            toolTipText = AndroidViewBundle.message("settings.groupCustomNodes.tooltip")
        }
        mainPanel.add(groupCustomNodesCheckBox, gbc)

        // Row 3: Label for groupings table
        gbc.gridy = 3
        gbc.insets = Insets(15, 5, 5, 5)
        val label = JLabel("Custom Node Groupings:")
        mainPanel.add(label, gbc)

        // Row 4: Groupings table
        gbc.gridy = 4
        gbc.weighty = 1.0
        gbc.fill = GridBagConstraints.BOTH
        gbc.insets = Insets(5, 5, 5, 5)

        groupingsTableModel = GroupingsTableModel()
        groupingsTable = JBTable(groupingsTableModel).apply {
            setShowGrid(true)
            setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        }

        val tablePanel = ToolbarDecorator.createDecorator(groupingsTable!!)
            .setAddAction { addGrouping() }
            .setRemoveAction { removeSelectedGrouping() }
            .setEditAction { editSelectedGrouping() }
            .setMoveUpAction(null)
            .setMoveDownAction(null)
            .createPanel()

        mainPanel.add(tablePanel, gbc)

        panel = mainPanel
        return mainPanel
    }

    private fun updateGroupingsTableState() {
        val enabled = showCustomFilesCheckBox?.isSelected ?: false
        groupingsTable?.isEnabled = enabled
    }

    private fun updateGroupCustomNodesState() {
        val enabled = showCustomFilesCheckBox?.isSelected ?: false
        groupCustomNodesCheckBox?.isEnabled = enabled
    }

    private fun addGrouping() {
        val parentPanel = panel ?: return
        val dialog = GroupingEditorDialog(parentPanel, null)
        if (dialog.showAndGet()) {
            val grouping = dialog.getGrouping()
            if (grouping != null) {
                groupingsTableModel?.addGrouping(grouping)
            }
        }
    }

    private fun removeSelectedGrouping() {
        val selectedRow = groupingsTable?.selectedRow ?: -1
        if (selectedRow >= 0) {
            groupingsTableModel?.removeGrouping(selectedRow)
        }
    }

    private fun editSelectedGrouping() {
        val selectedRow = groupingsTable?.selectedRow ?: -1
        if (selectedRow >= 0) {
            val currentGrouping = groupingsTableModel?.getGroupingAt(selectedRow) ?: return
            val parentPanel = panel ?: return
            val dialog = GroupingEditorDialog(parentPanel, currentGrouping)
            if (dialog.showAndGet()) {
                val updatedGrouping = dialog.getGrouping()
                if (updatedGrouping != null) {
                    groupingsTableModel?.updateGrouping(selectedRow, updatedGrouping)
                }
            }
        }
    }

    override fun isModified(): Boolean {
        val settings = AndroidViewSettings.getInstance()

        if (showBuildDirectoryCheckBox?.isSelected != settings.showBuildDirectory) {
            return true
        }

        if (showCustomFilesCheckBox?.isSelected != settings.showCustomFiles) {
            return true
        }

        if (groupCustomNodesCheckBox?.isSelected != settings.groupCustomNodes) {
            return true
        }

        val currentGroupings = groupingsTableModel?.getGroupings() ?: emptyList()
        if (currentGroupings != settings.customGroupings) {
            return true
        }

        return false
    }

    override fun apply() {
        val settings = AndroidViewSettings.getInstance()

        settings.showBuildDirectory = showBuildDirectoryCheckBox?.isSelected ?: false
        settings.showCustomFiles = showCustomFilesCheckBox?.isSelected ?: false
        settings.groupCustomNodes = groupCustomNodesCheckBox?.isSelected ?: true
        settings.customGroupings = groupingsTableModel?.getGroupings()?.toMutableList() ?: mutableListOf()

        // Refresh all open projects to reflect the changes
        ProjectManager.getInstance().openProjects
            .filter { !it.isDisposed }
            .forEach { project ->
                ProjectView.getInstance(project).currentProjectViewPane?.updateFromRoot(true)
            }
    }

    override fun reset() {
        val settings = AndroidViewSettings.getInstance()

        showBuildDirectoryCheckBox?.isSelected = settings.showBuildDirectory
        showCustomFilesCheckBox?.isSelected = settings.showCustomFiles
        groupCustomNodesCheckBox?.isSelected = settings.groupCustomNodes
        groupingsTableModel?.setGroupings(settings.customGroupings.map { it.copy() })

        updateGroupingsTableState()
        updateGroupCustomNodesState()
    }

    /**
     * Table model for managing groupings.
     */
    private class GroupingsTableModel : AbstractTableModel() {
        private val groupings = mutableListOf<CustomNodeGrouping>()

        override fun getRowCount(): Int = groupings.size

        override fun getColumnCount(): Int = 2

        override fun getColumnName(column: Int): String = when (column) {
            0 -> "Grouping Name"
            1 -> "File Patterns"
            else -> ""
        }

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val grouping = groupings[rowIndex]
            return when (columnIndex) {
                0 -> grouping.name
                1 -> grouping.patterns.joinToString(", ")
                else -> ""
            }
        }

        fun addGrouping(grouping: CustomNodeGrouping) {
            groupings.add(grouping)
            fireTableRowsInserted(groupings.size - 1, groupings.size - 1)
        }

        fun removeGrouping(index: Int) {
            if (index >= 0 && index < groupings.size) {
                groupings.removeAt(index)
                fireTableRowsDeleted(index, index)
            }
        }

        fun updateGrouping(index: Int, grouping: CustomNodeGrouping) {
            if (index >= 0 && index < groupings.size) {
                groupings[index] = grouping
                fireTableRowsUpdated(index, index)
            }
        }

        fun getGroupingAt(index: Int): CustomNodeGrouping? {
            return if (index >= 0 && index < groupings.size) groupings[index] else null
        }

        fun getGroupings(): List<CustomNodeGrouping> = groupings.toList()

        fun setGroupings(newGroupings: List<CustomNodeGrouping>) {
            groupings.clear()
            groupings.addAll(newGroupings)
            fireTableDataChanged()
        }
    }
}
