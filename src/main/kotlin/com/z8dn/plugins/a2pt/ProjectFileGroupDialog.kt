package com.z8dn.plugins.a2pt

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JComponent
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.table.AbstractTableModel

/**
 * Dialog for adding or editing a custom file group with its patterns.
 */
class ProjectFileGroupDialog(
    existingGroup: ProjectFileGroup? = null
) : DialogWrapper(true) {

    private val groupNameField: JBTextField = JBTextField()
    private val patternsTable: JBTable
    private val patternsTableModel: PatternsTableModel

    init {
        title = if (existingGroup == null)
            AndroidViewBundle.message("dialog.projectFileGroup.title.add")
        else
            AndroidViewBundle.message("dialog.projectFileGroup.title.edit")

        patternsTableModel = PatternsTableModel()
        patternsTable = JBTable(patternsTableModel).apply {
            setShowGrid(true)
            setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
            preferredScrollableViewportSize = Dimension(400, 150)
        }

        // Load existing data if editing
        existingGroup?.let {
            groupNameField.text = it.groupName
            patternsTableModel.setPatterns(it.patterns.toMutableList())
        }

        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints()
        gbc.gridx = 0
        gbc.gridy = 0
        gbc.anchor = GridBagConstraints.WEST
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.insets = JBUI.insets(5)

        // Group name label
        panel.add(JBLabel(AndroidViewBundle.message("dialog.projectFileGroup.groupName.label")), gbc)

        // Group name field
        gbc.gridy++
        gbc.weightx = 1.0
        groupNameField.preferredSize = Dimension(400, groupNameField.preferredSize.height)
        panel.add(groupNameField, gbc)

        // Patterns section label
        gbc.gridy++
        gbc.insets = JBUI.insets(15, 5, 5, 5)
        panel.add(JBLabel(AndroidViewBundle.message("dialog.projectFileGroup.patterns.label")), gbc)

        // Patterns table with toolbar
        gbc.gridy++
        gbc.fill = GridBagConstraints.BOTH
        gbc.weighty = 1.0
        gbc.insets = JBUI.insets(5)

        val decorator = ToolbarDecorator.createDecorator(patternsTable)
            .setAddAction { addPattern() }
            .setRemoveAction { removePattern() }
            .setEditAction { editPattern() }
            .disableUpDownActions()

        panel.add(decorator.createPanel(), gbc)

        // Help text
        gbc.gridy++
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.weighty = 0.0
        val helpLabel = JBLabel("<html><i>${AndroidViewBundle.message("dialog.projectFileGroup.patterns.help")}</i></html>")
        panel.add(helpLabel, gbc)

        return panel
    }

    private fun addPattern() {
        val pattern = JOptionPane.showInputDialog(
            contentPanel,
            AndroidViewBundle.message("dialog.addPattern.message"),
            AndroidViewBundle.message("dialog.addPattern.title"),
            JOptionPane.PLAIN_MESSAGE
        )
        if (!pattern.isNullOrBlank()) {
            patternsTableModel.addPattern(pattern.trim())
        }
    }

    private fun removePattern() {
        val selectedRow = patternsTable.selectedRow
        if (selectedRow >= 0) {
            patternsTableModel.removePattern(selectedRow)
        }
    }

    private fun editPattern() {
        val selectedRow = patternsTable.selectedRow
        if (selectedRow >= 0) {
            val currentPattern = patternsTableModel.getPattern(selectedRow)
            val newPattern = JOptionPane.showInputDialog(
                contentPanel,
                AndroidViewBundle.message("dialog.editPattern.message"),
                currentPattern
            )
            if (!newPattern.isNullOrBlank()) {
                patternsTableModel.updatePattern(selectedRow, newPattern.trim())
            }
        }
    }

    override fun doValidate(): ValidationInfo? {
        val groupName = groupNameField.text.trim()
        if (groupName.isEmpty()) {
            return ValidationInfo(AndroidViewBundle.message("dialog.validation.groupNameEmpty"), groupNameField)
        }

        if (patternsTableModel.getPatterns().isEmpty()) {
            return ValidationInfo(AndroidViewBundle.message("dialog.validation.patternsEmpty"), patternsTable)
        }

        return null
    }

    /**
     * Returns the configured ProjectFileGroup from the dialog.
     */
    fun getProjectFileGroup(): ProjectFileGroup {
        return ProjectFileGroup(
            groupName = groupNameField.text.trim(),
            patterns = patternsTableModel.getPatterns()
        )
    }

    /**
     * Table model for managing file patterns.
     */
    private class PatternsTableModel : AbstractTableModel() {
        private val patterns = mutableListOf<String>()

        override fun getRowCount(): Int = patterns.size

        override fun getColumnCount(): Int = 1

        override fun getColumnName(column: Int): String = AndroidViewBundle.message("dialog.projectFileGroup.table.columnName")

        override fun getValueAt(rowIndex: Int, columnIndex: Int): String = patterns[rowIndex]

        fun addPattern(pattern: String) {
            patterns.add(pattern)
            fireTableRowsInserted(patterns.size - 1, patterns.size - 1)
        }

        fun removePattern(index: Int) {
            patterns.removeAt(index)
            fireTableRowsDeleted(index, index)
        }

        fun updatePattern(index: Int, pattern: String) {
            patterns[index] = pattern
            fireTableRowsUpdated(index, index)
        }

        fun getPattern(index: Int): String = patterns[index]

        fun getPatterns(): MutableList<String> = patterns.toMutableList()

        fun setPatterns(newPatterns: MutableList<String>) {
            patterns.clear()
            patterns.addAll(newPatterns)
            fireTableDataChanged()
        }
    }
}
