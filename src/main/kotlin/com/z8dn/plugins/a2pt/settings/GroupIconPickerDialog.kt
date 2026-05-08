package com.z8dn.plugins.a2pt.settings

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.z8dn.plugins.a2pt.AndroidViewBundle
import com.z8dn.plugins.a2pt.utils.GroupIconCatalog
import com.z8dn.plugins.a2pt.utils.GroupIconEntry
import java.awt.Component
import java.awt.Dimension
import javax.swing.BorderFactory
import javax.swing.DefaultListModel
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.ListCellRenderer
import javax.swing.ListSelectionModel
import javax.swing.SwingConstants

class GroupIconPickerDialog(
    project: Project?,
    initialKey: String?
) : DialogWrapper(project, true) {

    private val listModel = DefaultListModel<GroupIconEntry>().apply {
        GroupIconCatalog.entries.forEach { addElement(it) }
    }

    private val iconList: JBList<GroupIconEntry> = JBList(listModel).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        layoutOrientation = JList.HORIZONTAL_WRAP
        visibleRowCount = -1
        fixedCellWidth = 48
        fixedCellHeight = 48
        cellRenderer = IconCellRenderer()
    }

    init {
        title = AndroidViewBundle.message("dialog.GroupIconPicker.title")

        val initialIndex = (0 until listModel.size())
            .firstOrNull { listModel.getElementAt(it).key == initialKey }
            ?: if (listModel.size() > 0) 0 else -1
        if (initialIndex >= 0) {
            iconList.selectedIndex = initialIndex
            iconList.ensureIndexIsVisible(initialIndex)
        }

        init()
    }

    override fun createCenterPanel(): JComponent {
        return JBScrollPane(iconList).apply {
            preferredSize = Dimension(8 * 48 + JBUI.scale(40), 6 * 48 + JBUI.scale(20))
            border = BorderFactory.createEmptyBorder()
        }
    }

    override fun getPreferredFocusedComponent(): JComponent = iconList

    fun getSelectedKey(): String? = iconList.selectedValue?.key

    private class IconCellRenderer : ListCellRenderer<GroupIconEntry> {
        private val label = JBLabel().apply {
            horizontalAlignment = SwingConstants.CENTER
            isOpaque = true
            border = BorderFactory.createEmptyBorder(4, 4, 4, 4)
        }

        override fun getListCellRendererComponent(
            list: JList<out GroupIconEntry>,
            value: GroupIconEntry,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): Component {
            label.icon = value.icon
            label.toolTipText = value.displayName
            label.background =
                if (isSelected) UIUtil.getListSelectionBackground(true) else list.background
            label.foreground =
                if (isSelected) UIUtil.getListSelectionForeground(true) else list.foreground
            return label
        }
    }
}
