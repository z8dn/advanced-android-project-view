package com.z8dn.plugins.a2pt.settings

import com.z8dn.plugins.a2pt.AndroidViewBundle
import com.z8dn.plugins.a2pt.utils.AndroidViewNodeUtils

import com.intellij.icons.AllIcons
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.CollectionListModel
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.ColoredTableCellRenderer
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.JBColor
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.table.JBTable
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.AsyncProcessIcon
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.NamedColorUtil
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.DefaultCellEditor
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.ListSelectionModel
import javax.swing.SwingConstants
import javax.swing.event.DocumentEvent
import javax.swing.table.AbstractTableModel

/**
 * Dialog for adding or editing a custom file group with its patterns.
 *
 * Patterns are edited in place and every keystroke re-runs [ProjectFileGroupPreview], so the
 * files a group will produce — and the per-pattern counts that explain them — are visible
 * before the group is saved. Without that, a mistyped glob produces a group that silently
 * does not appear in the tree at all.
 */
class ProjectFileGroupDialog(
    project: Project? = null,
    existingGroup: ProjectFileGroup? = null,
    private val siblingGroups: List<ProjectFileGroup> = emptyList()
) : DialogWrapper(project, true) {

    private val groupNameField = JBTextField()
    private val patternsTableModel = PatternsTableModel()
    private val patternsTable: JBTable

    /** Overrides the pattern-derived icon in the tree; [FileGroupIconOption.AUTO] leaves it derived. */
    private val iconComboBox = ComboBox(FileGroupIconOption.entries.toTypedArray()).apply {
        renderer = SimpleListCellRenderer.create { label, value, _ ->
            label.text = value.displayName
            label.icon = value.icon
        }
    }

    private val previewModel = CollectionListModel<PreviewRow>()
    private val previewList = JBList(previewModel)
    private val matchCountLabel = JBLabel()
    private val truncationLabel = JBLabel()
    private val scanningIcon = AsyncProcessIcon(PREVIEW_SPINNER_NAME)
    private val showExcludedCheckBox = JBCheckBox(
        AndroidViewBundle.message("dialog.ProjectFileGroup.Preview.showExcluded"),
        PropertiesComponent.getInstance().getBoolean(SHOW_EXCLUDED_KEY, false)
    )

    /** Every open project, so the preview can be pointed at a different one. */
    private val availableProjects: List<Project> =
        ProjectManager.getInstance().openProjects.filter { !it.isDisposed }

    private var previewProject: Project? =
        project?.takeIf { !it.isDisposed } ?: availableProjects.firstOrNull()

    private var lastResult: PreviewResult? = null

    /**
     * Debounces recomputation. Runs on the EDT — the trigger is a Swing document event and the
     * scan itself moves off the EDT below. Queuing the same identity coalesces keystrokes into
     * one scan per [RECOMPUTE_DELAY_MS] window.
     */
    private val recomputeQueue by lazy {
        MergingUpdateQueue(
            RECOMPUTE_QUEUE_NAME,
            RECOMPUTE_DELAY_MS,
            true,
            // ANY_COMPONENT, not the preview list: the queue resolves modality when an update is
            // queued, and the first one is queued from init() before the dialog has a window.
            // Resolving against the list then would yield non-modal, and the update would sit
            // unfired for as long as the dialog is up.
            MergingUpdateQueue.ANY_COMPONENT,
            disposable
        )
    }

    /**
     * The cell editor's field. A document listener writes through on every keystroke rather
     * than waiting for the edit to commit, otherwise the preview is not live.
     */
    private val patternEditorField = JBTextField().apply {
        border = JBUI.Borders.empty(0, 2)
        document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                val row = patternsTable.editingRow
                if (row >= 0) {
                    patternsTableModel.setPatternWhileEditing(row, text.trim())
                    scheduleRecompute()
                }
            }
        })
    }

    init {
        title = if (existingGroup == null) {
            AndroidViewBundle.message("dialog.ProjectFileGroup.Add.text")
        } else {
            AndroidViewBundle.message("dialog.ProjectFileGroup.Edit.text")
        }

        patternsTable = JBTable(patternsTableModel).apply {
            setShowGrid(false)
            setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
            rowHeight = JBUI.scale(22)
            preferredScrollableViewportSize = Dimension(JBUI.scale(280), JBUI.scale(240))
            columnModel.getColumn(PATTERN_COLUMN).cellRenderer = PatternCellRenderer()
            columnModel.getColumn(PATTERN_COLUMN).cellEditor = DefaultCellEditor(patternEditorField)
            columnModel.getColumn(FILES_COLUMN).cellRenderer = FilesCellRenderer()
            columnModel.getColumn(FILES_COLUMN).maxWidth = JBUI.scale(70)
            columnModel.getColumn(FILES_COLUMN).preferredWidth = JBUI.scale(70)
        }

        previewList.cellRenderer = PreviewRowRenderer()
        previewList.selectionMode = ListSelectionModel.SINGLE_SELECTION

        showExcludedCheckBox.addActionListener {
            PropertiesComponent.getInstance().setValue(SHOW_EXCLUDED_KEY, showExcludedCheckBox.isSelected)
            renderRows()
        }

        Disposer.register(disposable, scanningIcon)

        existingGroup?.let {
            groupNameField.text = it.groupName
            patternsTableModel.setPatterns(it.patterns)
            iconComboBox.selectedItem = FileGroupIconOption.fromIconPath(it.customIconPath)
        }

        init()
        scheduleRecompute()
    }

    override fun createCenterPanel(): JComponent {
        val root = JPanel(BorderLayout()).apply {
            preferredSize = Dimension(JBUI.scale(760), JBUI.scale(420))
        }

        val namePanel = JPanel(BorderLayout(0, JBUI.scale(4))).apply {
            add(JBLabel(AndroidViewBundle.message("dialog.ProjectFileGroup.GroupName.text")), BorderLayout.NORTH)
            add(groupNameField, BorderLayout.CENTER)
        }

        val iconPanel = JPanel(BorderLayout(0, JBUI.scale(4))).apply {
            add(JBLabel(AndroidViewBundle.message("dialog.ProjectFileGroup.Icon.text")), BorderLayout.NORTH)
            add(iconComboBox, BorderLayout.CENTER)
        }

        // Name takes the slack; the picker keeps its preferred width on the right.
        val headerPanel = JPanel(BorderLayout(JBUI.scale(8), 0)).apply {
            border = JBUI.Borders.emptyBottom(10)
            add(namePanel, BorderLayout.CENTER)
            add(iconPanel, BorderLayout.EAST)
        }
        root.add(headerPanel, BorderLayout.NORTH)

        val splitter = OnePixelSplitter(false, SPLITTER_PROPORTION).apply {
            firstComponent = createPatternsPanel()
            secondComponent = createPreviewPanel()
        }
        root.add(splitter, BorderLayout.CENTER)

        val helpLabel = JBLabel(
            "<html><i>${AndroidViewBundle.message("dialog.ProjectFileGroup.Patterns.description")}</i></html>"
        ).apply {
            border = JBUI.Borders.emptyTop(8)
            foreground = NamedColorUtil.getInactiveTextColor()
        }
        root.add(helpLabel, BorderLayout.SOUTH)

        return root
    }

    private fun createPatternsPanel(): JComponent {
        val decorator = ToolbarDecorator.createDecorator(patternsTable)
            .setAddAction { addPattern() }
            .setRemoveAction { removePattern() }
            .disableUpDownActions()

        return JPanel(BorderLayout(0, JBUI.scale(4))).apply {
            border = JBUI.Borders.emptyRight(8)
            add(JBLabel(AndroidViewBundle.message("dialog.ProjectFileGroup.Patterns.text")), BorderLayout.NORTH)
            add(decorator.createPanel(), BorderLayout.CENTER)
        }
    }

    private fun createPreviewPanel(): JComponent {
        val header = JPanel(BorderLayout()).apply {
            add(matchCountLabel, BorderLayout.WEST)
            add(createHeaderTrailer(), BorderLayout.EAST)
        }

        truncationLabel.foreground = NamedColorUtil.getInactiveTextColor()
        val footer = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.emptyTop(4)
            add(showExcludedCheckBox, BorderLayout.WEST)
            add(truncationLabel, BorderLayout.EAST)
        }

        return JPanel(BorderLayout(0, JBUI.scale(4))).apply {
            border = JBUI.Borders.emptyLeft(8)
            add(header, BorderLayout.NORTH)
            add(JBScrollPane(previewList), BorderLayout.CENTER)
            add(footer, BorderLayout.SOUTH)
        }
    }

    /** Spinner, plus a project picker — but only when picking is a real choice. */
    private fun createHeaderTrailer(): JComponent {
        val trailer = JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(6), 0))

        scanningIcon.isVisible = false
        trailer.add(scanningIcon)

        when {
            availableProjects.size > 1 -> {
                trailer.add(
                    JBLabel(AndroidViewBundle.message("dialog.ProjectFileGroup.Preview.projectLabel"))
                        .apply { foreground = NamedColorUtil.getInactiveTextColor() }
                )
                trailer.add(ComboBox(availableProjects.toTypedArray()).apply {
                    renderer = SimpleListCellRenderer.create("") { it.name }
                    selectedItem = previewProject
                    addActionListener {
                        previewProject = selectedItem as? Project
                        scheduleRecompute()
                    }
                })
            }

            previewProject != null -> {
                trailer.add(
                    JBLabel(
                        AndroidViewBundle.message(
                            "dialog.ProjectFileGroup.Preview.inProject",
                            previewProject?.name.orEmpty()
                        )
                    ).apply { foreground = NamedColorUtil.getInactiveTextColor() }
                )
            }
        }
        return trailer
    }

    // region pattern editing

    private fun addPattern() {
        stopEditing()
        val row = patternsTableModel.addPattern("")
        patternsTable.setRowSelectionInterval(row, row)
        patternsTable.scrollRectToVisible(patternsTable.getCellRect(row, PATTERN_COLUMN, true))
        patternsTable.editCellAt(row, PATTERN_COLUMN)
        patternsTable.editorComponent?.requestFocusInWindow()
    }

    private fun removePattern() {
        val row = patternsTable.selectedRow
        if (row < 0) return
        stopEditing()
        patternsTableModel.removePattern(row)
        scheduleRecompute()
    }

    private fun stopEditing() {
        if (patternsTable.isEditing) patternsTable.cellEditor?.stopCellEditing()
    }

    // endregion

    // region preview

    private fun scheduleRecompute() {
        recomputeQueue.queue(Update.create(RECOMPUTE_QUEUE_NAME) { recompute() })
    }

    private fun recompute() {
        val target = previewProject?.takeIf { !it.isDisposed }
        if (target == null) {
            renderNoProject()
            return
        }

        val patterns = patternsTableModel.patterns()
        val siblingInclusions = siblingGroups.flatMap { it.patterns }.filter { !it.startsWith("!") }

        scanningIcon.isVisible = true
        // Keep the previous list visible while scanning — blanking it makes every keystroke flash.
        previewList.setPaintBusy(true)

        ReadAction.nonBlocking<PreviewResult> {
            ProjectFileGroupPreview.compute(target, patterns, siblingInclusions)
        }
            // Cancels the in-flight scan as soon as a newer edit lands.
            .coalesceBy(this)
            .expireWith(disposable)
            // Without stateForComponent the callback never arrives under a modal dialog.
            .finishOnUiThread(ModalityState.stateForComponent(rootPane)) { result -> render(result) }
            .submit(AppExecutorUtil.getAppExecutorService())
    }

    private fun render(result: PreviewResult) {
        lastResult = result
        scanningIcon.isVisible = false
        previewList.setPaintBusy(false)
        patternsTableModel.setStats(result.stats)
        renderRows()
        initValidation()
    }

    private fun renderNoProject() {
        lastResult = null
        scanningIcon.isVisible = false
        previewList.setPaintBusy(false)
        patternsTableModel.setStats(emptyList())
        previewModel.replaceAll(emptyList())
        previewList.emptyText.text = AndroidViewBundle.message("dialog.ProjectFileGroup.Preview.noProject")
        matchCountLabel.text = AndroidViewBundle.message("dialog.ProjectFileGroup.Preview.title")
        truncationLabel.text = ""
    }

    private fun renderRows() {
        val result = lastResult ?: return
        val rows = if (showExcludedCheckBox.isSelected) result.rows else result.includedRows
        previewModel.replaceAll(rows)

        matchCountLabel.text =
            AndroidViewBundle.message("dialog.ProjectFileGroup.Preview.matchCount", result.matched)

        truncationLabel.text = if (result.truncated) {
            AndroidViewBundle.message(
                "dialog.ProjectFileGroup.Preview.truncated",
                ProjectFileGroupPreview.ROW_CAP,
                result.matched
            )
        } else {
            ""
        }

        updateEmptyText(result)
    }

    /** The idle / no-matches / invalid states all live in the list's empty text. */
    private fun updateEmptyText(result: PreviewResult) {
        val status = previewList.emptyText
        val projectName = previewProject?.name ?: ""

        when {
            patternsTableModel.patterns().none { it.isNotBlank() } ->
                status.text = AndroidViewBundle.message("dialog.ProjectFileGroup.Preview.empty")

            result.stats.any { it.kind == PatternKind.INVALID } ->
                status.text = AndroidViewBundle.message("dialog.ProjectFileGroup.Preview.invalid")

            result.matched == 0 -> {
                status.setText(
                    AndroidViewBundle.message("dialog.ProjectFileGroup.Preview.noMatches", projectName),
                    SimpleTextAttributes.REGULAR_ATTRIBUTES
                )
                // Name the consequence — an empty group does not merely look empty, it vanishes.
                status.appendLine(
                    AndroidViewBundle.message("dialog.ProjectFileGroup.Preview.noMatches.consequence")
                )
                status.appendLine(
                    AndroidViewBundle.message("dialog.ProjectFileGroup.Preview.noMatches.hint"),
                    SimpleTextAttributes.GRAYED_ATTRIBUTES,
                    null
                )
            }

            else -> status.text = ""
        }
    }

    // endregion

    override fun doValidate(): ValidationInfo? {
        val groupName = groupNameField.text.trim()
        if (groupName.isEmpty()) {
            return ValidationInfo(AndroidViewBundle.message("dialog.Validation.GroupNameEmpty.text"), groupNameField)
        }

        if (siblingGroups.any { it.groupName.trim().equals(groupName, ignoreCase = true) }) {
            return ValidationInfo(
                AndroidViewBundle.message("dialog.Validation.DuplicateGroupName.text", groupName),
                groupNameField
            )
        }

        val patterns = patternsTableModel.patterns().filter { it.isNotBlank() }
        if (patterns.isEmpty()) {
            return ValidationInfo(AndroidViewBundle.message("dialog.Validation.PatternsEmpty.text"), patternsTable)
        }

        if (patterns.all { it.startsWith("!") }) {
            return ValidationInfo(AndroidViewBundle.message("dialog.Validation.NoInclusionPattern.text"), patternsTable)
        }

        // Blocking: a pattern that does not compile is unambiguously broken and the fix is local.
        // Checked directly rather than from the preview so it holds before the first scan lands
        // and with no project open.
        val invalid = patterns.firstOrNull { !AndroidViewNodeUtils.isValidGlob(it.removePrefix("!")) }
        if (invalid != null) {
            return ValidationInfo(
                AndroidViewBundle.message("dialog.Validation.InvalidGlob.text", invalid),
                patternsTable
            )
        }

        // Non-blocking: a pattern may legitimately target a file that does not exist yet.
        val deadPattern = lastResult?.stats?.firstOrNull { it.kind == PatternKind.EMPTY }
        if (deadPattern != null) {
            return ValidationInfo(
                AndroidViewBundle.message(
                    "dialog.Validation.PatternMatchesNothing.text",
                    deadPattern.pattern,
                    previewProject?.name ?: ""
                ),
                patternsTable
            ).asWarning()
        }

        return null
    }

    override fun getPreferredFocusedComponent(): JComponent = groupNameField

    /**
     * Returns the configured ProjectFileGroup from the dialog.
     */
    fun getProjectFileGroup(): ProjectFileGroup {
        stopEditing()
        val selectedIcon = iconComboBox.selectedItem as? FileGroupIconOption
        return ProjectFileGroup(
            groupName = groupNameField.text.trim(),
            patterns = patternsTableModel.patterns().filter { it.isNotBlank() }.toMutableList(),
            customIconPath = selectedIcon?.iconPath
        )
    }

    // region table model and renderers

    /**
     * Backs the patterns table. Column 0 is the editable pattern, column 1 the match count
     * that [ProjectFileGroupPreview] most recently reported for it.
     */
    private inner class PatternsTableModel : AbstractTableModel() {
        private val patterns = mutableListOf<String>()
        private var stats: List<PatternStat> = emptyList()

        override fun getRowCount(): Int = patterns.size

        override fun getColumnCount(): Int = 2

        override fun getColumnName(column: Int): String = when (column) {
            PATTERN_COLUMN -> AndroidViewBundle.message("dialog.ProjectFileGroup.Table.ColumnName.text")
            else -> AndroidViewBundle.message("dialog.ProjectFileGroup.Table.ColumnName.files")
        }

        override fun getColumnClass(columnIndex: Int): Class<*> = String::class.java

        override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean =
            columnIndex == PATTERN_COLUMN

        override fun getValueAt(rowIndex: Int, columnIndex: Int): String = when (columnIndex) {
            PATTERN_COLUMN -> patterns[rowIndex]
            else -> ""
        }

        override fun setValueAt(value: Any?, rowIndex: Int, columnIndex: Int) {
            if (columnIndex != PATTERN_COLUMN) return
            val text = (value as? String)?.trim() ?: return
            if (patterns[rowIndex] == text) return
            patterns[rowIndex] = text
            invalidateStats()
            fireTableRowsUpdated(rowIndex, rowIndex)
            scheduleRecompute()
        }

        /**
         * Writes a keystroke through without firing a row update, which would tear down the
         * active cell editor.
         */
        fun setPatternWhileEditing(rowIndex: Int, text: String) {
            if (rowIndex !in patterns.indices || patterns[rowIndex] == text) return
            patterns[rowIndex] = text
            invalidateStats()
            fireTableCellUpdated(rowIndex, FILES_COLUMN)
        }

        private fun invalidateStats() {
            if (stats.isEmpty()) return
            stats = emptyList()
            fireFilesColumnUpdated()
        }

        fun setStats(newStats: List<PatternStat>) {
            stats = newStats
            fireFilesColumnUpdated()
        }

        /**
         * Repaints only the counts. A blanket [fireTableDataChanged] would cancel an in-progress
         * cell edit every time a scan finished.
         */
        private fun fireFilesColumnUpdated() {
            for (row in patterns.indices) fireTableCellUpdated(row, FILES_COLUMN)
        }

        fun statAt(rowIndex: Int): PatternStat? = stats.getOrNull(rowIndex)

        fun patternAt(rowIndex: Int): String = patterns.getOrElse(rowIndex) { "" }

        fun patterns(): List<String> = patterns.toList()

        fun addPattern(pattern: String): Int {
            patterns.add(pattern)
            invalidateStats()
            fireTableRowsInserted(patterns.size - 1, patterns.size - 1)
            return patterns.size - 1
        }

        fun removePattern(index: Int) {
            patterns.removeAt(index)
            invalidateStats()
            fireTableRowsDeleted(index, index)
        }

        fun setPatterns(newPatterns: List<String>) {
            patterns.clear()
            patterns.addAll(newPatterns)
            stats = emptyList()
            fireTableDataChanged()
        }
    }

    /**
     * Classifies a row for rendering. Blank and invalid are derived from the pattern itself so
     * they are correct before the first scan lands and with no project open; the remaining
     * kinds come from the last scan.
     */
    private fun kindAt(row: Int): PatternKind? {
        val body = patternsTableModel.patternAt(row).removePrefix("!")
        return when {
            body.isBlank() -> PatternKind.BLANK
            !AndroidViewNodeUtils.isValidGlob(body) -> PatternKind.INVALID
            else -> patternsTableModel.statAt(row)?.kind
        }
    }

    /** Flags a dead or broken pattern at the row that caused it. */
    private inner class PatternCellRenderer : ColoredTableCellRenderer() {
        override fun customizeCellRenderer(
            table: JTable,
            value: Any?,
            selected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int
        ) {
            append(value as? String ?: "")
            icon = when (kindAt(row)) {
                PatternKind.INVALID -> AllIcons.General.Error
                PatternKind.EMPTY -> AllIcons.General.Warning
                else -> null
            }
        }
    }

    /** The match count, right-aligned so the digits line up down the column. */
    private inner class FilesCellRenderer : ColoredTableCellRenderer() {
        init {
            setTextAlign(SwingConstants.RIGHT)
        }

        override fun customizeCellRenderer(
            table: JTable,
            value: Any?,
            selected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int
        ) {
            val kind = kindAt(row) ?: return
            if (kind == PatternKind.BLANK) return

            val text = when (kind) {
                PatternKind.INVALID -> INVALID_COUNT_TEXT
                else -> patternsTableModel.statAt(row)?.count?.toString() ?: return
            }

            // JBColor, never a raw Color, or the column is unreadable in Darcula.
            val foreground = when (kind) {
                PatternKind.INVALID -> ERROR_FOREGROUND
                PatternKind.EMPTY -> WARNING_FOREGROUND
                PatternKind.NEGATION -> NamedColorUtil.getInactiveTextColor()
                else -> null
            }

            // Let the selection colours win when the row is highlighted.
            val attributes = if (selected || foreground == null) {
                SimpleTextAttributes.REGULAR_ATTRIBUTES
            } else {
                SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, foreground)
            }
            append(text, attributes)
        }
    }

    /**
     * Renders a preview row with the same attributes `ProjectFileNode.update` uses, so the
     * preview and the tree read identically by construction rather than by coincidence.
     */
    private class PreviewRowRenderer : ColoredListCellRenderer<PreviewRow>() {
        override fun customizeCellRenderer(
            list: javax.swing.JList<out PreviewRow>,
            value: PreviewRow,
            index: Int,
            selected: Boolean,
            hasFocus: Boolean
        ) {
            icon = value.file.fileType.icon

            val excludedBy = value.excludedBy
            if (excludedBy == null) {
                append(value.file.name, SimpleTextAttributes.REGULAR_ATTRIBUTES)
                append(" (${value.qualifier})", SimpleTextAttributes.GRAY_ATTRIBUTES)
            } else {
                append(value.file.name, STRIKETHROUGH_ATTRIBUTES)
                append(
                    " (" + AndroidViewBundle.message(
                        "dialog.ProjectFileGroup.Preview.excludedBy",
                        excludedBy
                    ) + ")",
                    SimpleTextAttributes.GRAY_ATTRIBUTES
                )
            }
        }
    }

    // endregion

    private companion object {
        const val PATTERN_COLUMN = 0
        const val FILES_COLUMN = 1

        const val RECOMPUTE_DELAY_MS = 250
        const val RECOMPUTE_QUEUE_NAME = "ProjectFileGroupPreviewRecompute"
        const val SPLITTER_PROPORTION = 0.45f
        const val INVALID_COUNT_TEXT = "—"
        const val PREVIEW_SPINNER_NAME = "ProjectFileGroupPreview"
        const val SHOW_EXCLUDED_KEY = "com.z8dn.plugins.a2pt.preview.showExcluded"

        val WARNING_FOREGROUND = JBColor(Color(0xB8, 0x6A, 0x00), Color(0xD9, 0xA3, 0x43))
        val ERROR_FOREGROUND = JBColor(Color(0xC7, 0x22, 0x2D), Color(0xFF, 0x63, 0x6E))

        val STRIKETHROUGH_ATTRIBUTES = SimpleTextAttributes(
            SimpleTextAttributes.STYLE_STRIKEOUT,
            UIUtil.getInactiveTextColor()
        )
    }
}
