# Dynamic Icons for File Groups — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let users override the icon used for a `ProjectFileGroup` from a curated set of `AllIcons`, with the existing auto-detection as fallback.

**Architecture:** Add an optional `iconKey: String?` field to `ProjectFileGroup`. A new `GroupIconCatalog` registry maps stable string keys to curated `AllIcons` entries. `ProjectFileGroupNode` first consults the catalog by key, falling back to the existing auto-detection. A new `GroupIconPickerDialog` modal shows the catalog as a grid; the existing `ProjectFileGroupDialog` adds a compact "Icon: [preview] [Choose…] [Reset to auto]" row.

**Tech Stack:** Kotlin 2.3.0, JDK 21, IntelliJ Platform Gradle Plugin 2.14.0, Swing, JUnit 4.

**Spec:** [`docs/superpowers/specs/2026-05-07-dynamic-icons-design.md`](../specs/2026-05-07-dynamic-icons-design.md)

---

## File Map

| File | Status | Responsibility |
|------|--------|----------------|
| `src/main/kotlin/com/z8dn/plugins/a2pt/settings/AndroidViewSettings.kt` | Modified | Add `iconKey` to `ProjectFileGroup` |
| `src/main/kotlin/com/z8dn/plugins/a2pt/utils/GroupIconCatalog.kt` | New | Curated AllIcons registry + `find(key)` lookup |
| `src/main/kotlin/com/z8dn/plugins/a2pt/utils/GroupIconResolver.kt` | New | Pure resolver: catalog override → auto-detect fallback |
| `src/main/kotlin/com/z8dn/plugins/a2pt/nodes/ProjectFileGroupNode.kt` | Modified | Use `GroupIconResolver` instead of inline auto-detect |
| `src/main/kotlin/com/z8dn/plugins/a2pt/settings/GroupIconPickerDialog.kt` | New | Modal grid picker |
| `src/main/kotlin/com/z8dn/plugins/a2pt/settings/ProjectFileGroupDialog.kt` | Modified | Add icon-row UI |
| `src/main/kotlin/com/z8dn/plugins/a2pt/settings/AndroidViewSettingsConfigurable.kt` | Modified | Add icon column to settings table |
| `src/main/resources/messages/AndroidViewBundle.properties` | Modified | New i18n keys for icon UI |
| `src/test/kotlin/com/z8dn/plugins/a2pt/utils/GroupIconCatalogTest.kt` | New | Catalog API contract tests |
| `src/test/kotlin/com/z8dn/plugins/a2pt/utils/GroupIconResolverTest.kt` | New | Resolver branching tests |
| `CHANGELOG.md` | Modified | `[Unreleased]` entry |

---

## Task 1: Add `iconKey` field to `ProjectFileGroup`

**Files:**
- Modify: `src/main/kotlin/com/z8dn/plugins/a2pt/settings/AndroidViewSettings.kt`

This is a pure data-class field addition. No test — Kotlin data class behavior plus `XmlSerializerUtil.copyBean` is library-tested. Existing serialized state lacks the field; default `null` preserves auto-detect.

- [ ] **Step 1: Add `iconKey` field**

Modify the `ProjectFileGroup` data class:

```kotlin
data class ProjectFileGroup(
    var groupName: String = "",
    var patterns: MutableList<String> = mutableListOf(),
    var iconKey: String? = null
)
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew compileKotlin -q`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/com/z8dn/plugins/a2pt/settings/AndroidViewSettings.kt
git commit -m "feat: add optional iconKey to ProjectFileGroup"
```

---

## Task 2: Create `GroupIconCatalog` with TDD

**Files:**
- Test: `src/test/kotlin/com/z8dn/plugins/a2pt/utils/GroupIconCatalogTest.kt`
- Create: `src/main/kotlin/com/z8dn/plugins/a2pt/utils/GroupIconCatalog.kt`

The catalog is a singleton object exposing `entries` (List<GroupIconEntry>) and `find(key)`. We test the API contract, not the icon objects themselves — those are loaded reflectively via `runCatching` and may vary per IDE version.

- [ ] **Step 1: Write failing tests**

Create `src/test/kotlin/com/z8dn/plugins/a2pt/utils/GroupIconCatalogTest.kt`:

```kotlin
package com.z8dn.plugins.a2pt.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupIconCatalogTest {

    @Test
    fun `find returns null for null key`() {
        assertNull(GroupIconCatalog.find(null))
    }

    @Test
    fun `find returns null for unknown key`() {
        assertNull(GroupIconCatalog.find("does.not.exist"))
    }

    @Test
    fun `entries is non-empty`() {
        assertTrue("Catalog should expose at least one entry", GroupIconCatalog.entries.isNotEmpty())
    }

    @Test
    fun `every entry is findable by its own key`() {
        for (entry in GroupIconCatalog.entries) {
            val found = GroupIconCatalog.find(entry.key)
            assertNotNull("Expected to find entry for key=${entry.key}", found)
            assertEquals(entry.key, found?.key)
        }
    }

    @Test
    fun `entry display names are non-blank`() {
        for (entry in GroupIconCatalog.entries) {
            assertTrue("Display name blank for key=${entry.key}", entry.displayName.isNotBlank())
        }
    }
}
```

- [ ] **Step 2: Run tests — should fail to compile (no `GroupIconCatalog`)**

Run: `./gradlew test --tests "com.z8dn.plugins.a2pt.utils.GroupIconCatalogTest" -q`
Expected: compilation error: `Unresolved reference 'GroupIconCatalog'`.

- [ ] **Step 3: Implement `GroupIconCatalog`**

Create `src/main/kotlin/com/z8dn/plugins/a2pt/utils/GroupIconCatalog.kt`:

```kotlin
package com.z8dn.plugins.a2pt.utils

import com.intellij.icons.AllIcons
import javax.swing.Icon

/**
 * One entry in the curated catalog: a stable [key] used for persistence,
 * a human-readable [displayName] shown in the picker, and the bound [icon].
 */
data class GroupIconEntry(
    val key: String,
    val displayName: String,
    val icon: Icon
)

/**
 * Curated set of [com.intellij.icons.AllIcons] entries that can be assigned
 * as a custom icon to a project file group.
 *
 * Each entry is constructed defensively via [runCatching] so that a missing
 * `AllIcons` member on a future or older IDE silently filters that entry
 * out instead of breaking class initialization.
 */
object GroupIconCatalog {

    val entries: List<GroupIconEntry> by lazy { buildEntries() }

    fun find(key: String?): GroupIconEntry? =
        key?.let { k -> entries.firstOrNull { it.key == k } }

    private fun buildEntries(): List<GroupIconEntry> {
        // (key, displayName, icon-supplier) — supplier is invoked inside runCatching
        val candidates: List<Triple<String, String, () -> Icon>> = listOf(
            Triple("nodes.module",            "Module",          { AllIcons.Nodes.Module }),
            Triple("nodes.package",           "Package",         { AllIcons.Nodes.Package }),
            Triple("nodes.folder",            "Folder",          { AllIcons.Nodes.Folder }),
            Triple("nodes.configFolder",      "Config Folder",   { AllIcons.Nodes.ConfigFolder }),
            Triple("nodes.dataSchema",        "Database",        { AllIcons.Nodes.DataSchema }),
            Triple("nodes.dataTables",        "Tables",          { AllIcons.Nodes.DataTables }),
            Triple("nodes.plugin",            "Plugin",          { AllIcons.Nodes.Plugin }),
            Triple("nodes.test",              "Test",            { AllIcons.Nodes.JunitTestMark }),
            Triple("nodes.console",           "Console",         { AllIcons.Nodes.Console }),
            Triple("nodes.gradle",            "Gradle",          { AllIcons.Nodes.Gradle }),
            Triple("nodes.bookmark",          "Bookmark",        { AllIcons.Nodes.Bookmark }),
            Triple("general.settings",        "Settings",        { AllIcons.General.Settings }),
            Triple("general.gearPlain",       "Gear",            { AllIcons.General.GearPlain }),
            Triple("general.web",             "Web",             { AllIcons.General.Web }),
            Triple("general.note",            "Note",            { AllIcons.General.Note }),
            Triple("general.information",     "Info",            { AllIcons.General.Information }),
            Triple("general.warning",         "Warning",         { AllIcons.General.Warning }),
            Triple("general.error",           "Error",           { AllIcons.General.Error }),
            Triple("general.user",            "User",            { AllIcons.General.User }),
            Triple("general.locate",          "Locate",          { AllIcons.General.Locate }),
            Triple("general.contextHelp",     "Help",            { AllIcons.General.ContextHelp }),
            Triple("actions.startDebugger",   "Debug",           { AllIcons.Actions.StartDebugger }),
            Triple("actions.execute",         "Run",             { AllIcons.Actions.Execute }),
            Triple("actions.lightning",       "Lightning",       { AllIcons.Actions.Lightning }),
            Triple("actions.find",            "Find",            { AllIcons.Actions.Find }),
            Triple("actions.upload",          "Upload",          { AllIcons.Actions.Upload }),
            Triple("actions.download",        "Download",        { AllIcons.Actions.Download }),
            Triple("actions.lock",            "Lock",            { AllIcons.Actions.Lock }),
            Triple("fileTypes.archive",       "Archive",         { AllIcons.FileTypes.Archive }),
            Triple("fileTypes.config",        "Config",          { AllIcons.FileTypes.Config }),
            Triple("fileTypes.json",          "JSON",            { AllIcons.FileTypes.Json }),
            Triple("fileTypes.xml",           "XML",             { AllIcons.FileTypes.Xml }),
            Triple("fileTypes.yaml",          "YAML",            { AllIcons.FileTypes.Yaml }),
            Triple("fileTypes.text",          "Text",            { AllIcons.FileTypes.Text }),
            Triple("fileTypes.idea",          "IntelliJ",        { AllIcons.FileTypes.Idea }),
            Triple("toolwindows.documentation", "Documentation", { AllIcons.Toolwindows.Documentation }),
            Triple("toolwindows.todoView",    "Todo",            { AllIcons.Toolwindows.ToolWindowTodoView }),
            Triple("toolwindows.problems",    "Problems",        { AllIcons.Toolwindows.Problems }),
            Triple("toolwindows.changes",     "Version Control", { AllIcons.Toolwindows.ToolWindowChanges }),
            Triple("vcs.branch",              "Branch",          { AllIcons.Vcs.Branch }),
            Triple("debugger.console",        "Debug Console",   { AllIcons.Debugger.Console })
        )

        return candidates.mapNotNull { (key, displayName, supplier) ->
            runCatching { GroupIconEntry(key, displayName, supplier()) }.getOrNull()
        }
    }
}
```

- [ ] **Step 4: Run tests — should pass**

Run: `./gradlew test --tests "com.z8dn.plugins.a2pt.utils.GroupIconCatalogTest" -q`
Expected: `BUILD SUCCESSFUL`, all 5 tests pass.

If `entries is non-empty` fails (e.g. `AllIcons` not loadable in test JVM), switch the test class to extend `BasePlatformTestCase` and re-run. The other tests should still pass either way.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/z8dn/plugins/a2pt/utils/GroupIconCatalog.kt \
        src/test/kotlin/com/z8dn/plugins/a2pt/utils/GroupIconCatalogTest.kt
git commit -m "feat: add GroupIconCatalog with curated AllIcons entries"
```

---

## Task 3: Extract resolver and rewire `ProjectFileGroupNode` (TDD)

**Files:**
- Test: `src/test/kotlin/com/z8dn/plugins/a2pt/utils/GroupIconResolverTest.kt`
- Create: `src/main/kotlin/com/z8dn/plugins/a2pt/utils/GroupIconResolver.kt`
- Modify: `src/main/kotlin/com/z8dn/plugins/a2pt/nodes/ProjectFileGroupNode.kt:69-91` (replace `getGroupIcon`)

The auto-detection branch needs `FileTypeManager.getInstance()` (an IntelliJ application service), so we keep that part inside `ProjectFileGroupNode`. The resolver is a pure function: `iconKey` + an `autoDetect: () -> Icon` lambda. That's trivially testable without any IntelliJ test framework.

- [ ] **Step 1: Write failing resolver tests**

Create `src/test/kotlin/com/z8dn/plugins/a2pt/utils/GroupIconResolverTest.kt`:

```kotlin
package com.z8dn.plugins.a2pt.utils

import org.junit.Assert.assertSame
import org.junit.Test
import javax.swing.Icon
import javax.swing.ImageIcon

class GroupIconResolverTest {

    private val sentinelAuto: Icon = ImageIcon()
    private val autoDetect: () -> Icon = { sentinelAuto }

    @Test
    fun `null iconKey falls back to auto-detect`() {
        assertSame(sentinelAuto, GroupIconResolver.resolve(null, autoDetect))
    }

    @Test
    fun `unknown iconKey falls back to auto-detect`() {
        assertSame(sentinelAuto, GroupIconResolver.resolve("does.not.exist", autoDetect))
    }

    @Test
    fun `known iconKey returns the catalog icon`() {
        // Pick the first available entry from the catalog so the test
        // is robust to entries being filtered out on future/older IDEs.
        val entry = GroupIconCatalog.entries.firstOrNull()
            ?: error("Catalog is empty — cannot run this test")
        val resolved = GroupIconResolver.resolve(entry.key, autoDetect)
        assertSame(entry.icon, resolved)
    }
}
```

- [ ] **Step 2: Run tests — should fail (no `GroupIconResolver`)**

Run: `./gradlew test --tests "com.z8dn.plugins.a2pt.utils.GroupIconResolverTest" -q`
Expected: compilation error: `Unresolved reference 'GroupIconResolver'`.

- [ ] **Step 3: Create `GroupIconResolver`**

Create `src/main/kotlin/com/z8dn/plugins/a2pt/utils/GroupIconResolver.kt`:

```kotlin
package com.z8dn.plugins.a2pt.utils

import javax.swing.Icon

/**
 * Resolves the icon for a project file group. Returns the catalog entry's icon
 * when [iconKey] is non-null and present in [GroupIconCatalog]; otherwise calls
 * [autoDetect] for the fallback icon.
 */
object GroupIconResolver {
    fun resolve(iconKey: String?, autoDetect: () -> Icon): Icon =
        GroupIconCatalog.find(iconKey)?.icon ?: autoDetect()
}
```

- [ ] **Step 4: Run tests — should pass**

Run: `./gradlew test --tests "com.z8dn.plugins.a2pt.utils.GroupIconResolverTest" -q`
Expected: 3 tests pass.

- [ ] **Step 5: Rewire `ProjectFileGroupNode.getGroupIcon`**

In `src/main/kotlin/com/z8dn/plugins/a2pt/nodes/ProjectFileGroupNode.kt`, add an import and replace the body of `getGroupIcon`. The method goes from this:

```kotlin
private fun getGroupIcon(): Icon {
    val inclusionPatterns = fileGroup.patterns.filter { !it.startsWith("!") }
    if (inclusionPatterns.size == 1) {
        val pattern = inclusionPatterns[0]
        val fileTypeManager = FileTypeManager.getInstance()

        // Handle wildcard patterns like "*.md"
        if (pattern.startsWith("*.")) {
            val extension = pattern.substring(2)
            val fileType = fileTypeManager.getFileTypeByExtension(extension)
            return fileType.icon ?: AllIcons.FileTypes.Text
        }

        // Handle exact filename patterns like "LICENSE"
        if (!pattern.contains("*") && !pattern.contains("/")) {
            val fileType = fileTypeManager.getFileTypeByFileName(pattern)
            return fileType.icon ?: AllIcons.FileTypes.Text
        }
    }

    // Default to folder icon for multiple patterns or complex wildcards
    return AllIcons.Nodes.Folder
}
```

To this:

```kotlin
private fun getGroupIcon(): Icon =
    GroupIconResolver.resolve(fileGroup.iconKey, ::autoDetectIcon)

private fun autoDetectIcon(): Icon {
    val inclusionPatterns = fileGroup.patterns.filter { !it.startsWith("!") }
    if (inclusionPatterns.size == 1) {
        val pattern = inclusionPatterns[0]
        val fileTypeManager = FileTypeManager.getInstance()

        if (pattern.startsWith("*.")) {
            val extension = pattern.substring(2)
            val fileType = fileTypeManager.getFileTypeByExtension(extension)
            return fileType.icon ?: AllIcons.FileTypes.Text
        }

        if (!pattern.contains("*") && !pattern.contains("/")) {
            val fileType = fileTypeManager.getFileTypeByFileName(pattern)
            return fileType.icon ?: AllIcons.FileTypes.Text
        }
    }

    return AllIcons.Nodes.Folder
}
```

Add the import at the top of the file (alphabetically with the others):

```kotlin
import com.z8dn.plugins.a2pt.utils.GroupIconResolver
```

- [ ] **Step 6: Verify the build is green**

Run: `./gradlew compileKotlin -q && ./gradlew test --tests "com.z8dn.plugins.a2pt.utils.*" -q`
Expected: `BUILD SUCCESSFUL`, all utils tests pass.

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/com/z8dn/plugins/a2pt/utils/GroupIconResolver.kt \
        src/test/kotlin/com/z8dn/plugins/a2pt/utils/GroupIconResolverTest.kt \
        src/main/kotlin/com/z8dn/plugins/a2pt/nodes/ProjectFileGroupNode.kt
git commit -m "feat: route group-icon lookup through GroupIconResolver"
```

---

## Task 4: Add i18n bundle keys

**Files:**
- Modify: `src/main/resources/messages/AndroidViewBundle.properties`

Add five new keys for the icon UI. Keep them grouped near the existing `dialog.ProjectFileGroup.*` keys.

- [ ] **Step 1: Append new keys**

Open `src/main/resources/messages/AndroidViewBundle.properties` and add the following block immediately after the line `dialog.ProjectFileGroup.Patterns.description=...` (line 23):

```properties
dialog.ProjectFileGroup.Icon.label=Icon:
dialog.ProjectFileGroup.Icon.choose=Choose…
dialog.ProjectFileGroup.Icon.reset=Reset to auto
dialog.ProjectFileGroup.Icon.autoDetected=(Auto-detected)
dialog.GroupIconPicker.title=Pick Group Icon
```

(Note: `…` is `…`. The bundle file is ISO-8859-1 by default, so non-ASCII characters need Unicode escapes.)

- [ ] **Step 2: Verify keys load**

Run: `./gradlew compileKotlin -q`
Expected: `BUILD SUCCESSFUL`. (The bundle is loaded reflectively so a malformed properties file won't fail compile, but a syntax error will fail at runtime — visual inspection is enough at this stage.)

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/messages/AndroidViewBundle.properties
git commit -m "feat(i18n): add bundle keys for group icon picker"
```

---

## Task 5: Create `GroupIconPickerDialog`

**Files:**
- Create: `src/main/kotlin/com/z8dn/plugins/a2pt/settings/GroupIconPickerDialog.kt`

A modal `DialogWrapper` showing the catalog as a horizontal-wrapping `JBList`. Pre-selects the entry whose key matches `initialKey`, or the first entry otherwise. OK returns the selected key; Cancel returns null.

- [ ] **Step 1: Write the dialog**

Create `src/main/kotlin/com/z8dn/plugins/a2pt/settings/GroupIconPickerDialog.kt`:

```kotlin
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

/**
 * Modal picker for choosing one of the curated group icons.
 *
 * @param project Optional parent project (used for dialog focus/positioning).
 * @param initialKey Currently-selected icon key, if any.
 */
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

        // Pre-select initial key, or first entry, or none if catalog is empty.
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
        val scroll = JBScrollPane(iconList).apply {
            preferredSize = Dimension(8 * 48 + JBUI.scale(40), 6 * 48 + JBUI.scale(20))
            border = BorderFactory.createEmptyBorder()
        }
        return scroll
    }

    override fun getPreferredFocusedComponent(): JComponent = iconList

    /** Returns the key of the selected entry, or null if no selection. */
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
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew compileKotlin -q`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/com/z8dn/plugins/a2pt/settings/GroupIconPickerDialog.kt
git commit -m "feat: add GroupIconPickerDialog modal grid picker"
```

---

## Task 6: Add icon row to `ProjectFileGroupDialog`

**Files:**
- Modify: `src/main/kotlin/com/z8dn/plugins/a2pt/settings/ProjectFileGroupDialog.kt`

Insert a new row between the group-name field and the patterns section:
`[Icon: ] [16x16 preview] (display name or "(Auto-detected)") [Choose…] [Reset to auto]`.

- [ ] **Step 1: Add imports**

In `ProjectFileGroupDialog.kt` add these imports (alphabetically with the existing imports):

```kotlin
import com.intellij.icons.AllIcons
import com.z8dn.plugins.a2pt.utils.GroupIconCatalog
import javax.swing.JButton
```

- [ ] **Step 2: Add icon-row state**

Inside the class, near the other private fields (`groupNameField`, `patternsTable`, `patternsTableModel`), add:

```kotlin
private var currentIconKey: String? = null
private val iconPreviewLabel: JBLabel = JBLabel()
private val resetIconButton: JButton =
    JButton(AndroidViewBundle.message("dialog.ProjectFileGroup.Icon.reset"))
private val chooseIconButton: JButton =
    JButton(AndroidViewBundle.message("dialog.ProjectFileGroup.Icon.choose"))
```

- [ ] **Step 3: Seed icon-row state in `init`**

Inside the `init { ... }` block, replace the existing seed section so that `existingGroup` also seeds `currentIconKey`. Find this:

```kotlin
existingGroup?.let {
    groupNameField.text = it.groupName
    patternsTableModel.setPatterns(it.patterns.toMutableList())
}
```

Replace with:

```kotlin
existingGroup?.let {
    groupNameField.text = it.groupName
    patternsTableModel.setPatterns(it.patterns.toMutableList())
    currentIconKey = it.iconKey
}

chooseIconButton.addActionListener {
    val dialog = GroupIconPickerDialog(null, currentIconKey)
    if (dialog.showAndGet()) {
        currentIconKey = dialog.getSelectedKey()
        refreshIconRow()
    }
}
resetIconButton.addActionListener {
    currentIconKey = null
    refreshIconRow()
}
refreshIconRow()
```

- [ ] **Step 4: Add `refreshIconRow` helper**

Add this method to the class (place it near `addPattern`/`editPattern`):

```kotlin
private fun refreshIconRow() {
    val entry = GroupIconCatalog.find(currentIconKey)
    if (entry != null) {
        iconPreviewLabel.icon = entry.icon
        iconPreviewLabel.text = entry.displayName
    } else {
        iconPreviewLabel.icon = AllIcons.Nodes.Folder
        iconPreviewLabel.text =
            AndroidViewBundle.message("dialog.ProjectFileGroup.Icon.autoDetected")
    }
    resetIconButton.isEnabled = currentIconKey != null
}
```

- [ ] **Step 5: Wire icon row into `createCenterPanel`**

After the group-name field block (the `panel.add(groupNameField, gbc)` line), insert a new icon-row block before the "Patterns section label" comment:

```kotlin
// Icon row label
gbc.gridy++
gbc.weightx = 0.0
gbc.fill = GridBagConstraints.NONE
gbc.insets = JBUI.insets(15, 5, 5, 5)
panel.add(JBLabel(AndroidViewBundle.message("dialog.ProjectFileGroup.Icon.label")), gbc)

// Icon row content (preview + buttons)
gbc.gridy++
gbc.weightx = 1.0
gbc.fill = GridBagConstraints.HORIZONTAL
gbc.insets = JBUI.insets(5)
val iconRow = JPanel(java.awt.FlowLayout(java.awt.FlowLayout.LEFT, JBUI.scale(8), 0)).apply {
    add(iconPreviewLabel)
    add(chooseIconButton)
    add(resetIconButton)
}
panel.add(iconRow, gbc)
```

- [ ] **Step 6: Persist `iconKey` in `getProjectFileGroup`**

Update the existing `getProjectFileGroup` to include `iconKey`:

```kotlin
fun getProjectFileGroup(): ProjectFileGroup {
    return ProjectFileGroup(
        groupName = groupNameField.text.trim(),
        patterns = patternsTableModel.getPatterns(),
        iconKey = currentIconKey
    )
}
```

- [ ] **Step 7: Verify build**

Run: `./gradlew compileKotlin -q`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

```bash
git add src/main/kotlin/com/z8dn/plugins/a2pt/settings/ProjectFileGroupDialog.kt
git commit -m "feat: add icon picker row to Edit File Group dialog"
```

---

## Task 7: Add icon column to settings table

**Files:**
- Modify: `src/main/kotlin/com/z8dn/plugins/a2pt/settings/AndroidViewSettingsConfigurable.kt`

The existing `ProjectFileGroupsTableModel` exposes 2 columns (Group Name, Patterns). Insert a new column 0 that returns `Icon` so `JBTable` uses its default icon renderer. We also need to read the auto-detected icon for groups whose `iconKey` is null. Since `FileTypeManager.getInstance()` requires the application service (which is always available in IDE runtime), this is fine inside the configurable.

- [ ] **Step 1: Add imports**

At the top of `AndroidViewSettingsConfigurable.kt` add:

```kotlin
import com.intellij.icons.AllIcons
import com.intellij.openapi.fileTypes.FileTypeManager
import com.z8dn.plugins.a2pt.utils.GroupIconCatalog
import javax.swing.Icon
```

- [ ] **Step 2: Update the table model**

In the inner `ProjectFileGroupsTableModel` class:

Change `getColumnCount`:
```kotlin
override fun getColumnCount(): Int = 3
```

Change `getColumnName`:
```kotlin
override fun getColumnName(column: Int): String = when (column) {
    0 -> ""
    1 -> AndroidViewBundle.message("settings.Table.ColumnName.groupName")
    2 -> AndroidViewBundle.message("settings.Table.ColumnName.patterns")
    else -> ""
}
```

Add a `getColumnClass` override (so `JBTable` picks the icon renderer for column 0):
```kotlin
override fun getColumnClass(columnIndex: Int): Class<*> = when (columnIndex) {
    0 -> Icon::class.java
    else -> String::class.java
}
```

Replace `getValueAt` so column 0 returns the resolved icon:
```kotlin
override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
    val group = groups[rowIndex]
    return when (columnIndex) {
        0 -> resolveIcon(group)
        1 -> group.groupName
        2 -> group.patterns.joinToString(", ")
        else -> ""
    }
}

private fun resolveIcon(group: ProjectFileGroup): Icon {
    GroupIconCatalog.find(group.iconKey)?.let { return it.icon }
    val inclusionPatterns = group.patterns.filter { !it.startsWith("!") }
    if (inclusionPatterns.size == 1) {
        val pattern = inclusionPatterns[0]
        val fileTypeManager = FileTypeManager.getInstance()
        if (pattern.startsWith("*.")) {
            val ext = pattern.substring(2)
            return fileTypeManager.getFileTypeByExtension(ext).icon ?: AllIcons.FileTypes.Text
        }
        if (!pattern.contains("*") && !pattern.contains("/")) {
            return fileTypeManager.getFileTypeByFileName(pattern).icon ?: AllIcons.FileTypes.Text
        }
    }
    return AllIcons.Nodes.Folder
}
```

(This duplicates the auto-detect logic from `ProjectFileGroupNode`. The duplication is acceptable here — both call sites depend on `FileTypeManager` and we don't want a circular dependency between `nodes/` and `settings/`. Future refactor: extract the auto-detect into a shared helper. Out of scope for this PR.)

- [ ] **Step 3: Pin column 0 to a small width**

In `createGroupsTablePanel`, after the `JBTable` construction (at the line `groupsTable = JBTable(groupsTableModel).apply { ... }`), add a follow-up block that sets the column widths:

```kotlin
groupsTable!!.columnModel.getColumn(0).apply {
    preferredWidth = JBUI.scale(28)
    maxWidth = JBUI.scale(28)
    minWidth = JBUI.scale(28)
}
```

You'll also need this import:
```kotlin
import com.intellij.util.ui.JBUI
```

- [ ] **Step 4: Verify build**

Run: `./gradlew compileKotlin -q`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/z8dn/plugins/a2pt/settings/AndroidViewSettingsConfigurable.kt
git commit -m "feat: show resolved icon in settings file-groups table"
```

---

## Task 8: Update CHANGELOG

**Files:**
- Modify: `CHANGELOG.md`

The plugin's `[Unreleased]` section drives marketplace release notes via the gradle-changelog plugin.

- [ ] **Step 1: Add bullet under `[Unreleased]`**

Open `CHANGELOG.md`. Replace the section:

```markdown
## [Unreleased]

## [0.0.8] - 2026-02-12
```

with:

```markdown
## [Unreleased]

### Added

- Custom icons for project file groups: pick from a curated set of IntelliJ icons in the Edit File Group dialog. Falls back to the existing auto-detection when no icon is selected. ([#31](https://github.com/z8dn/advanced-android-project-view/issues/31))

## [0.0.8] - 2026-02-12
```

- [ ] **Step 2: Commit**

```bash
git add CHANGELOG.md
git commit -m "docs: add Unreleased changelog entry for dynamic icons"
```

---

## Task 9: Final verification

**Files:** none (verification only)

- [ ] **Step 1: Run the full test suite**

Run: `./gradlew test`
Expected: `BUILD SUCCESSFUL`. All existing + new tests pass.

If `GroupIconCatalogTest.entries is non-empty` or `GroupIconResolverTest.known iconKey returns the catalog icon` fails because `AllIcons.*` cannot be loaded in the plain JUnit JVM, change those test classes to extend `com.intellij.testFramework.fixtures.BasePlatformTestCase` (which boots a minimal IntelliJ application) and re-run. This is a known wrinkle of testing IntelliJ static icon fields outside the platform fixture.

- [ ] **Step 2: Run full check**

Run: `./gradlew check`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Smoke-test in a development IDE**

Run: `./gradlew runIde`
In the launched Android Studio sandbox:
1. Open any Android project.
2. Tools → Customize Android Tree View… → Add a file group with patterns `*.md`. Confirm the icon row shows "(Auto-detected)" with a Markdown icon preview.
3. Click "Choose…", pick "Settings", click OK. Preview updates to the gear icon and "Settings" label.
4. Save settings. The project tree group renders with the gear icon. The settings table's icon column shows the gear icon for that row.
5. Edit the group again, click "Reset to auto", save. The tree falls back to the auto-detected Markdown icon.
6. Restart the sandbox IDE and verify the chosen icon persists across restarts.

If anything misbehaves, fix and commit before opening the PR.

- [ ] **Step 4: Verify plugin structure**

Run: `./gradlew verifyPlugin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Push branch and open PR**

```bash
git push -u origin feat/dynamic-icons-issue-31
```

Then create the PR from this branch into `main`. Title: `feat: dynamic icons for file groups (#31)`. Body should describe the feature, link issue #31, and include a screenshot of the Edit File Group dialog with the icon row visible.

---

## Out of scope

- Custom uploaded SVG/PNG icons (file picker).
- Searchable browsing of all `AllIcons.*`.
- Search scope icons (`ProjectFileGroupScope` does not surface icons).
- Per-pattern icons inside a single group.
- Drag-to-reorder, favorites, or recently-used in the picker.

These are documented as out-of-scope in the spec.
