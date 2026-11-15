package com.z8dn.plugins.a2pt.settings

import com.z8dn.plugins.a2pt.AndroidViewBundle

import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.ide.impl.ProjectUtil
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
            .addExtraAction(importAction())
            .addExtraAction(exportAction())

        panel.add(decorator.createPanel(), BorderLayout.CENTER)

        val helpLabel = JBLabel("<html><i>${AndroidViewBundle.message("settings.CustomFileGroups.description")}</i></html>")
        panel.add(helpLabel, BorderLayout.SOUTH)

        return panel
    }

    /**
     * This is an application-level configurable, so no project is in scope. The preview needs
     * one to scan; fall back to any open project rather than showing nothing.
     */
    private fun previewProject(): Project? =
        ProjectUtil.getActiveProject()?.takeIf { !it.isDisposed }
            ?: ProjectManager.getInstance().openProjects.firstOrNull { !it.isDisposed }

    /**
     * Siblings come from the table model rather than [AndroidViewSettings]: the draft state is
     * what OK will actually save, so it is what the preview must be exact against.
     */
    private fun siblingGroups(excludingRow: Int = -1): List<ProjectFileGroup> =
        groupsTableModel?.getGroups().orEmpty()
            .filterIndexed { index, _ -> index != excludingRow }

    private fun addGroup() {
        val dialog = ProjectFileGroupDialog(previewProject(), null, siblingGroups())
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
            val dialog = ProjectFileGroupDialog(
                previewProject(),
                currentGroup,
                siblingGroups(excludingRow = selectedRow)
            )
            if (dialog.showAndGet()) {
                groupsTableModel?.updateGroup(selectedRow, dialog.getProjectFileGroup())
            }
        }
    }

    // region import / export

    private fun importAction(): AnAction = object : DumbAwareAction(
        AndroidViewBundle.message("settings.ImportExport.Import.text"),
        AndroidViewBundle.message("settings.ImportExport.Import.description"),
        AllIcons.ToolbarDecorator.Import
    ) {
        override fun getActionUpdateThread() = ActionUpdateThread.EDT
        override fun actionPerformed(e: AnActionEvent) = importGroups()
    }

    private fun exportAction(): AnAction = object : DumbAwareAction(
        AndroidViewBundle.message("settings.ImportExport.Export.text"),
        AndroidViewBundle.message("settings.ImportExport.Export.description"),
        AllIcons.ToolbarDecorator.Export
    ) {
        override fun getActionUpdateThread() = ActionUpdateThread.EDT
        override fun update(e: AnActionEvent) {
            e.presentation.isEnabled = (groupsTableModel?.rowCount ?: 0) > 0
        }

        override fun actionPerformed(e: AnActionEvent) = exportGroups()
    }

    /** `<project>/.a2pt`, or the user home when no project is open. */
    private fun defaultDirectory(project: Project?): Path {
        val base = project?.basePath?.let { Paths.get(it) } ?: Paths.get(System.getProperty("user.home"))
        return if (project?.basePath != null) base.resolve(ProjectFileGroupsSerializer.DEFAULT_DIRECTORY) else base
    }

    /** The default directory if it exists yet, otherwise its nearest existing ancestor. */
    private fun existingDirectory(dir: Path): Path {
        var current: Path? = dir
        while (current != null && !Files.isDirectory(current)) current = current.parent
        return current ?: dir
    }

    private fun exportGroups() {
        val groups = groupsTableModel?.getGroups().orEmpty()
        if (groups.isEmpty()) return
        val project = previewProject()

        val descriptor = FileSaverDescriptor(
            AndroidViewBundle.message("settings.ImportExport.Export.chooser.title"),
            AndroidViewBundle.message("settings.ImportExport.Export.chooser.description"),
            "json"
        )
        // The dialog can only open in a directory that exists, so create the dedicated one up
        // front and remove it again if the user cancels or saves elsewhere.
        val defaultDir = defaultDirectory(project)
        val createdDefaultDir = !Files.exists(defaultDir) &&
            runCatching { Files.createDirectories(defaultDir) }.isSuccess
        val wrapper = try {
            FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project)
                .save(existingDirectory(defaultDir), ProjectFileGroupsSerializer.DEFAULT_FILE_NAME)
        } finally {
            // Files.delete refuses a non-empty directory, so this never removes user files.
            if (createdDefaultDir) runCatching { Files.delete(defaultDir) }
        }
        wrapper ?: return

        val target = wrapper.file.toPath()
        try {
            target.parent?.let { Files.createDirectories(it) }
            Files.write(target, ProjectFileGroupsSerializer.toJson(groups).toByteArray(StandardCharsets.UTF_8))
        } catch (e: IOException) {
            Messages.showErrorDialog(
                project,
                AndroidViewBundle.message("settings.ImportExport.Export.error.message", target, e.message ?: e.javaClass.simpleName),
                AndroidViewBundle.message("settings.ImportExport.Export.error.title")
            )
            return
        }
        // Make the new file visible in the project tree without waiting for a focus-driven refresh.
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(target)
    }

    private fun importGroups() {
        val model = groupsTableModel ?: return
        val project = previewProject()

        val start: VirtualFile? =
            LocalFileSystem.getInstance().refreshAndFindFileByNioFile(existingDirectory(defaultDirectory(project)))
        val descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor("json")
            .withTitle(AndroidViewBundle.message("settings.ImportExport.Import.chooser.title"))
            .withDescription(AndroidViewBundle.message("settings.ImportExport.Import.chooser.description"))
        val file = FileChooser.chooseFile(descriptor, project, start) ?: return

        val text = try {
            String(file.contentsToByteArray(), StandardCharsets.UTF_8)
        } catch (e: IOException) {
            showImportError(
                project,
                AndroidViewBundle.message("settings.ImportExport.Import.error.read", file.path, e.message ?: e.javaClass.simpleName)
            )
            return
        }

        when (val result = ProjectFileGroupsSerializer.fromJson(text.removePrefix("\uFEFF"))) {
            is ProjectFileGroupsSerializer.ImportResult.Failure ->
                showImportError(project, describe(result.reason))

            is ProjectFileGroupsSerializer.ImportResult.Success -> {
                val existing = model.getGroups()
                if (existing.isEmpty()) {
                    model.setGroups(result.groups)
                    return
                }
                val choice = Messages.showYesNoCancelDialog(
                    project,
                    AndroidViewBundle.message("settings.ImportExport.Import.mode.message"),
                    AndroidViewBundle.message("settings.ImportExport.Import.mode.title"),
                    AndroidViewBundle.message("settings.ImportExport.Import.mode.merge"),
                    AndroidViewBundle.message("settings.ImportExport.Import.mode.replace"),
                    AndroidViewBundle.message("settings.ImportExport.Import.mode.cancel"),
                    Messages.getQuestionIcon()
                )
                when (choice) {
                    Messages.YES -> model.setGroups(ProjectFileGroupsSerializer.merge(existing, result.groups))
                    Messages.NO -> model.setGroups(result.groups)
                    else -> Unit
                }
            }
        }
    }

    private fun showImportError(project: Project?, message: String) {
        Messages.showErrorDialog(project, message, AndroidViewBundle.message("settings.ImportExport.Import.error.title"))
    }

    private fun describe(reason: ProjectFileGroupsSerializer.FailureReason): String {
        val prefix = "settings.ImportExport.Import.failure."
        return when (reason) {
            is ProjectFileGroupsSerializer.FailureReason.MalformedJson ->
                AndroidViewBundle.message(prefix + "malformed", reason.detail ?: "?")

            is ProjectFileGroupsSerializer.FailureReason.InvalidStructure ->
                AndroidViewBundle.message(prefix + "structure", reason.detail)

            ProjectFileGroupsSerializer.FailureReason.MissingVersion ->
                AndroidViewBundle.message(prefix + "missingVersion")

            is ProjectFileGroupsSerializer.FailureReason.NewerVersion ->
                AndroidViewBundle.message(
                    prefix + "newerVersion",
                    reason.version,
                    ProjectFileGroupsSerializer.CURRENT_VERSION
                )

            is ProjectFileGroupsSerializer.FailureReason.DuplicateName ->
                AndroidViewBundle.message(prefix + "duplicateName", reason.name)

            is ProjectFileGroupsSerializer.FailureReason.InvalidGroup -> {
                val why = when (val problem = reason.problem) {
                    ProjectFileGroupsSerializer.GroupProblem.EmptyName ->
                        AndroidViewBundle.message(prefix + "group.emptyName")
                    ProjectFileGroupsSerializer.GroupProblem.NoPatterns ->
                        AndroidViewBundle.message(prefix + "group.noPatterns")
                    ProjectFileGroupsSerializer.GroupProblem.BlankPattern ->
                        AndroidViewBundle.message(prefix + "group.blankPattern")
                    ProjectFileGroupsSerializer.GroupProblem.NoInclusionPattern ->
                        AndroidViewBundle.message(prefix + "group.noInclusion")
                    is ProjectFileGroupsSerializer.GroupProblem.InvalidGlob ->
                        AndroidViewBundle.message(prefix + "group.invalidGlob", problem.pattern)
                }
                AndroidViewBundle.message(prefix + "group", reason.index + 1, reason.name, why)
            }
        }
    }

    // endregion

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
