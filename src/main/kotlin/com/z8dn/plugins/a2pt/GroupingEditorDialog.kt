package com.z8dn.plugins.a2pt

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.table.JBTable
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import javax.swing.*
import javax.swing.table.AbstractTableModel

/**
 * Dialog for editing a custom node grouping (name and file patterns).
 */
class GroupingEditorDialog(
    parent: Component,
    private val existingGrouping: CustomNodeGrouping?
) : DialogWrapper(parent, true) {

    private val nameField = JTextField(20)
    private val patternsTableModel = PatternsTableModel()
    private val patternsTable = JBTable(patternsTableModel)

    init {
        title = if (existingGrouping == null) "Add Grouping" else "Edit Grouping"
        init()

        // Load existing data if editing
        existingGrouping?.let {
            nameField.text = it.name
            patternsTableModel.setPatterns(it.patterns.toList())
        }
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout(10, 10))
        panel.preferredSize = Dimension(500, 400)

        // Name field panel
        val namePanel = JPanel(BorderLayout(5, 5))
        namePanel.add(JLabel("Grouping Name:"), BorderLayout.WEST)
        namePanel.add(nameField, BorderLayout.CENTER)
        panel.add(namePanel, BorderLayout.NORTH)

        // Patterns table
        val patternsPanel = JPanel(BorderLayout(5, 5))
        patternsPanel.add(JLabel("File Patterns:"), BorderLayout.NORTH)

        patternsTable.setShowGrid(true)
        patternsTable.selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION

        val tableDecorator = ToolbarDecorator.createDecorator(patternsTable)
            .setAddAction { addPattern() }
            .setRemoveAction { removeSelectedPattern() }
            .setEditAction { editSelectedPattern() }
            .setMoveUpAction(null)
            .setMoveDownAction(null)
            .createPanel()

        patternsPanel.add(tableDecorator, BorderLayout.CENTER)
        panel.add(patternsPanel, BorderLayout.CENTER)

        return panel
    }

    private fun addPattern() {
        val pattern = JOptionPane.showInputDialog(
            contentPane,
            "Enter file pattern (e.g., *.md, LICENSE, CHANGELOG.md):",
            "Add Pattern",
            JOptionPane.PLAIN_MESSAGE
        )

        if (!pattern.isNullOrBlank()) {
            patternsTableModel.addPattern(pattern.trim())
        }
    }

    private fun removeSelectedPattern() {
        val selectedRow = patternsTable.selectedRow
        if (selectedRow >= 0) {
            patternsTableModel.removePattern(selectedRow)
        }
    }

    private fun editSelectedPattern() {
        val selectedRow = patternsTable.selectedRow
        if (selectedRow >= 0) {
            val currentPattern = patternsTableModel.getPatternAt(selectedRow) ?: return

            val newPattern = JOptionPane.showInputDialog(
                contentPane,
                "Edit file pattern:",
                currentPattern
            )

            if (!newPattern.isNullOrBlank()) {
                patternsTableModel.updatePattern(selectedRow, newPattern.trim())
            }
        }
    }

    override fun doValidate(): ValidationInfo? {
        if (nameField.text.isNullOrBlank()) {
            return ValidationInfo("Grouping name cannot be empty", nameField)
        }

        if (patternsTableModel.getPatterns().isEmpty()) {
            return ValidationInfo("At least one file pattern is required")
        }

        return null
    }

    fun getGrouping(): CustomNodeGrouping? {
        val name = nameField.text.trim()
        val patterns = patternsTableModel.getPatterns().toMutableList()

        return if (name.isNotEmpty() && patterns.isNotEmpty()) {
            CustomNodeGrouping(name, patterns)
        } else {
            null
        }
    }

    /**
     * Table model for file patterns within a grouping.
     */
    private class PatternsTableModel : AbstractTableModel() {
        private val patterns = mutableListOf<String>()

        override fun getRowCount(): Int = patterns.size

        override fun getColumnCount(): Int = 1

        override fun getColumnName(column: Int): String = "Pattern"

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any = patterns[rowIndex]

        fun addPattern(pattern: String) {
            patterns.add(pattern)
            fireTableRowsInserted(patterns.size - 1, patterns.size - 1)
        }

        fun removePattern(index: Int) {
            if (index >= 0 && index < patterns.size) {
                patterns.removeAt(index)
                fireTableRowsDeleted(index, index)
            }
        }

        fun updatePattern(index: Int, pattern: String) {
            if (index >= 0 && index < patterns.size) {
                patterns[index] = pattern
                fireTableRowsUpdated(index, index)
            }
        }

        fun getPatternAt(index: Int): String? {
            return if (index >= 0 && index < patterns.size) patterns[index] else null
        }

        fun getPatterns(): List<String> = patterns.toList()

        fun setPatterns(newPatterns: List<String>) {
            patterns.clear()
            patterns.addAll(newPatterns)
            fireTableDataChanged()
        }
    }
}
