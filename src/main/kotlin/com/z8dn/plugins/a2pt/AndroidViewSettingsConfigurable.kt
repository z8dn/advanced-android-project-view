package com.z8dn.plugins.a2pt

import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.ProjectManager
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.table.JBTable
import java.awt.BorderLayout
import javax.swing.*
import javax.swing.table.AbstractTableModel

/**
 * Settings page for Android View customization.
 * Allows users to configure build directory visibility and custom file patterns to display.
 */
class AndroidViewSettingsConfigurable : SearchableConfigurable {

    private var panel: JPanel? = null
    private var showBuildDirectoryCheckBox: JBCheckBox? = null
    private var showCustomFilesCheckBox: JBCheckBox? = null
    private var groupCustomNodesCheckBox: JBCheckBox? = null
    private var filePatternTable: JBTable? = null
    private var filePatternTableModel: FilePatternTableModel? = null

    override fun getId(): String = "com.z8dn.plugins.a2pt.settings"

    override fun getDisplayName(): String = "Advanced Android Project View"

    override fun createComponent(): JComponent {
        val mainPanel = JPanel(BorderLayout())
        mainPanel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)

        // Create top panel for checkboxes
        val topPanel = JPanel()
        topPanel.layout = BoxLayout(topPanel, BoxLayout.Y_AXIS)

        // Build directory checkbox
        showBuildDirectoryCheckBox = JBCheckBox(AndroidViewBundle.message("settings.showBuildDirectory")).apply {
            toolTipText = AndroidViewBundle.message("settings.showBuildDirectory.tooltip")
        }
        topPanel.add(showBuildDirectoryCheckBox)
        topPanel.add(Box.createVerticalStrut(10))

        // Custom files checkbox
        showCustomFilesCheckBox = JBCheckBox(AndroidViewBundle.message("settings.showCustomFiles")).apply {
            toolTipText = AndroidViewBundle.message("settings.showCustomFiles.tooltip")
            addActionListener {
                updateFilePatternTableState()
                updateGroupCustomNodesState()
            }
        }
        topPanel.add(showCustomFilesCheckBox)
        topPanel.add(Box.createVerticalStrut(10))

        // Group custom nodes checkbox
        groupCustomNodesCheckBox = JBCheckBox(AndroidViewBundle.message("settings.groupCustomNodes")).apply {
            toolTipText = AndroidViewBundle.message("settings.groupCustomNodes.tooltip")
        }
        topPanel.add(groupCustomNodesCheckBox)
        topPanel.add(Box.createVerticalStrut(10))

        mainPanel.add(topPanel, BorderLayout.NORTH)

        // Create file pattern table
        filePatternTableModel = FilePatternTableModel()
        filePatternTable = JBTable(filePatternTableModel).apply {
            setShowGrid(true)
            setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        }

        // Create toolbar decorator for add/remove/edit buttons
        val tablePanel = ToolbarDecorator.createDecorator(filePatternTable!!)
            .setAddAction { addFilePattern() }
            .setRemoveAction { removeSelectedPattern() }
            .setEditAction { editSelectedPattern() }
            .setMoveUpAction(null)
            .setMoveDownAction(null)
            .createPanel()

        // Add label and table panel
        val tableContainerPanel = JPanel(BorderLayout())
        tableContainerPanel.border = BorderFactory.createEmptyBorder(0, 20, 0, 0)

        val label = JLabel(AndroidViewBundle.message("settings.filePatterns.label"))
        tableContainerPanel.add(label, BorderLayout.NORTH)
        tableContainerPanel.add(tablePanel, BorderLayout.CENTER)

        mainPanel.add(tableContainerPanel, BorderLayout.CENTER)

        panel = mainPanel
        return mainPanel
    }

    private fun updateFilePatternTableState() {
        val enabled = showCustomFilesCheckBox?.isSelected ?: false
        filePatternTable?.isEnabled = enabled
    }

    private fun updateGroupCustomNodesState() {
        val enabled = showCustomFilesCheckBox?.isSelected ?: false
        groupCustomNodesCheckBox?.isEnabled = enabled
    }

    private fun addFilePattern() {
        val pattern = JOptionPane.showInputDialog(
            panel,
            AndroidViewBundle.message("dialog.addPattern.message"),
            AndroidViewBundle.message("dialog.addPattern.title"),
            JOptionPane.PLAIN_MESSAGE
        )

        if (!pattern.isNullOrBlank()) {
            filePatternTableModel?.addPattern(pattern.trim())
        }
    }

    private fun removeSelectedPattern() {
        val selectedRow = filePatternTable?.selectedRow ?: -1
        if (selectedRow >= 0) {
            filePatternTableModel?.removePattern(selectedRow)
        }
    }

    private fun editSelectedPattern() {
        val selectedRow = filePatternTable?.selectedRow ?: -1
        if (selectedRow >= 0) {
            val currentPattern = filePatternTableModel?.getPatternAt(selectedRow) ?: return

            val newPattern = JOptionPane.showInputDialog(
                panel,
                AndroidViewBundle.message("dialog.editPattern.message"),
                currentPattern
            )

            if (!newPattern.isNullOrBlank()) {
                filePatternTableModel?.updatePattern(selectedRow, newPattern.trim())
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

        val currentPatterns = filePatternTableModel?.getPatterns() ?: emptyList()
        if (currentPatterns != settings.filePatterns) {
            return true
        }

        return false
    }

    override fun apply() {
        val settings = AndroidViewSettings.getInstance()

        settings.showBuildDirectory = showBuildDirectoryCheckBox?.isSelected ?: false
        settings.showCustomFiles = showCustomFilesCheckBox?.isSelected ?: false
        settings.groupCustomNodes = groupCustomNodesCheckBox?.isSelected ?: true
        settings.filePatterns = filePatternTableModel?.getPatterns()?.toMutableList() ?: mutableListOf()

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
        filePatternTableModel?.setPatterns(settings.filePatterns.toList())

        updateFilePatternTableState()
        updateGroupCustomNodesState()
    }

    /**
     * Table model for managing file patterns.
     */
    private class FilePatternTableModel : AbstractTableModel() {
        private val patterns = mutableListOf<String>()

        override fun getRowCount(): Int = patterns.size

        override fun getColumnCount(): Int = 1

        override fun getColumnName(column: Int): String = AndroidViewBundle.message("settings.filePatterns.columnName")

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any = patterns[rowIndex]

        override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = true

        override fun setValueAt(aValue: Any?, rowIndex: Int, columnIndex: Int) {
            val value = (aValue as? String)?.trim().orEmpty()
            if (value.isNotBlank() && rowIndex in patterns.indices) {
                patterns[rowIndex] = value
                fireTableCellUpdated(rowIndex, columnIndex)
            }
        }

        fun addPattern(pattern: String) {
            patterns.add(pattern)
            fireTableRowsInserted(patterns.size - 1, patterns.size - 1)
        }

        fun removePattern(index: Int) {
            if (index in patterns.indices) {
                patterns.removeAt(index)
                fireTableRowsDeleted(index, index)
            }
        }

        fun getPatternAt(index: Int): String? {
            return if (index in patterns.indices) patterns[index] else null
        }

        fun updatePattern(index: Int, newPattern: String) {
            if (index in patterns.indices) {
                patterns[index] = newPattern
                fireTableCellUpdated(index, 0)
            }
        }

        fun getPatterns(): List<String> = patterns.toList()

        fun setPatterns(newPatterns: List<String>) {
            patterns.clear()
            patterns.addAll(newPatterns)
            fireTableDataChanged()
        }
    }
}
