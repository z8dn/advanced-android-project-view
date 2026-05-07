# Dynamic Icons for File Groups — Design Spec

**Issue:** [#31 — Feature: Dynamic Icons for File Groups](https://github.com/z8dn/advanced-android-project-view/issues/31)
**Date:** 2026-05-07
**Status:** Approved (pending implementation)

## Goal

Let users override the icon used for a `ProjectFileGroup` in the project tree. Today, `ProjectFileGroupNode.getGroupIcon()` auto-detects an icon from the group's patterns (single inclusion pattern → file-type icon, otherwise folder). Users want to assign a meaningful icon — Cloud, Database, Settings, etc. — that survives changes to the patterns.

## Non-goals

- Custom user-supplied icon files (SVG/PNG from disk). AllIcons only.
- Searchable browsing of all `AllIcons.*` (~2000 entries). Curated set only.
- Per-file (child node) icon overrides. Group node only.
- Search scope icons. `ProjectFileGroupScope` extends `RangeBasedLocalSearchScope`, which does not surface an icon.
- Drag-to-reorder, favorites, or recently-used in the picker.

## Decisions made during brainstorming

| # | Decision | Rationale |
|---|----------|-----------|
| 1 | Icons sourced from `AllIcons` only | Theme-aware (light/dark), DPI-aware, zero bundling, matches IDE look. The issue itself proposes this. |
| 2 | Curated grid (~40 icons) as the picker | Best UX/effort ratio. Searchable list of all icons is overkill; free-text path input has poor discoverability. |
| 3 | Icon override is optional; auto-detection is the default fallback | No migration needed for existing groups. Users who like the auto behavior keep it. |
| 4 | Picker UX = compact row in Edit File Group dialog + popup picker dialog (Layout B) | Idiomatic for IntelliJ (matches file/path/color choosers). Keeps parent dialog tight. "Reset to auto" is a first-class button, not a hidden tile. |

## Architecture

Three new pieces, two modified files, one data-model field.

```
settings/
  AndroidViewSettings.kt          (modified)  — adds iconKey to ProjectFileGroup
  ProjectFileGroupDialog.kt       (modified)  — adds icon row
  AndroidViewSettingsConfigurable.kt (modified) — adds icon column to settings table
  GroupIconPickerDialog.kt        (new)       — popup picker
nodes/
  ProjectFileGroupNode.kt         (modified)  — resolver: override → catalog → auto
utils/
  GroupIconCatalog.kt             (new)       — curated AllIcons registry
```

## Data model

```kotlin
data class ProjectFileGroup(
    var groupName: String = "",
    var patterns: MutableList<String> = mutableListOf(),
    var iconKey: String? = null   // NEW
)
```

`iconKey` is a stable string key (e.g. `"general.settings"`, `"nodes.module"`) that maps to a `GroupIconEntry` in the catalog. We never serialize `Icon` objects; we look them up at render time.

**Persistence.** `XmlSerializerUtil.copyBean` serializes the new field transparently. Pre-existing `androidViewSettings.xml` files written by older plugin versions have no `<option name="iconKey">` element, so the field defaults to `null` after load. No migration code is required.

## Icon catalog

New file `utils/GroupIconCatalog.kt`:

```kotlin
data class GroupIconEntry(val key: String, val displayName: String, val icon: Icon)

object GroupIconCatalog {
    val entries: List<GroupIconEntry> by lazy { buildEntries() }

    fun find(key: String?): GroupIconEntry? =
        key?.let { k -> entries.firstOrNull { it.key == k } }

    private fun buildEntries(): List<GroupIconEntry> {
        val candidates = listOf(
            "nodes.module"        to ("Module"        to { AllIcons.Nodes.Module }),
            "general.settings"    to ("Settings"      to { AllIcons.General.Settings }),
            // … see "Curated icon list" below
        )
        return candidates.mapNotNull { (key, pair) ->
            runCatching { GroupIconEntry(key, pair.first, pair.second()) }.getOrNull()
        }
    }
}
```

Each entry is constructed inside `runCatching` so a missing `AllIcons` member on a future or older IDE downgrades that entry to "not in the catalog on your IDE" rather than crashing class init.

### Curated icon list (~40)

Final list to verify against the SDK during implementation. Working list:

| Key                       | Display name      | AllIcons reference                  |
|---------------------------|-------------------|-------------------------------------|
| `nodes.module`            | Module            | `AllIcons.Nodes.Module`             |
| `nodes.package`           | Package           | `AllIcons.Nodes.Package`            |
| `nodes.folder`            | Folder            | `AllIcons.Nodes.Folder`             |
| `nodes.configFolder`      | Config Folder     | `AllIcons.Nodes.ConfigFolder`       |
| `nodes.dataSchema`        | Database          | `AllIcons.Nodes.DataSchema`         |
| `nodes.dataTables`        | Tables            | `AllIcons.Nodes.DataTables`         |
| `nodes.plugin`            | Plugin            | `AllIcons.Nodes.Plugin`             |
| `nodes.test`              | Test              | `AllIcons.Nodes.JunitTestMark`      |
| `nodes.console`           | Console           | `AllIcons.Nodes.Console`            |
| `nodes.gradle`            | Gradle            | `AllIcons.Nodes.Gradle`             |
| `general.settings`        | Settings          | `AllIcons.General.Settings`         |
| `general.gearPlain`       | Gear              | `AllIcons.General.GearPlain`        |
| `general.web`             | Web               | `AllIcons.General.Web`              |
| `general.note`            | Note              | `AllIcons.General.Note`             |
| `general.information`     | Info              | `AllIcons.General.Information`      |
| `general.warning`         | Warning           | `AllIcons.General.Warning`          |
| `general.error`           | Error             | `AllIcons.General.Error`            |
| `general.user`            | User              | `AllIcons.General.User`             |
| `general.locate`          | Locate            | `AllIcons.General.Locate`           |
| `nodes.bookmark`          | Bookmark          | `AllIcons.Nodes.Bookmark`           |
| `general.contextHelp`     | Help              | `AllIcons.General.ContextHelp`      |
| `actions.startDebugger`   | Debug             | `AllIcons.Actions.StartDebugger`    |
| `actions.execute`         | Run               | `AllIcons.Actions.Execute`          |
| `actions.lightning`       | Lightning         | `AllIcons.Actions.Lightning`        |
| `actions.find`            | Find              | `AllIcons.Actions.Find`             |
| `actions.upload`          | Upload            | `AllIcons.Actions.Upload`           |
| `actions.download`        | Download          | `AllIcons.Actions.Download`         |
| `actions.lock`            | Lock              | `AllIcons.Actions.Lock`             |
| `fileTypes.archive`       | Archive           | `AllIcons.FileTypes.Archive`        |
| `fileTypes.config`        | Config            | `AllIcons.FileTypes.Config`         |
| `fileTypes.json`          | JSON              | `AllIcons.FileTypes.Json`           |
| `fileTypes.xml`           | XML               | `AllIcons.FileTypes.Xml`            |
| `fileTypes.yaml`          | YAML              | `AllIcons.FileTypes.Yaml`           |
| `fileTypes.text`          | Text              | `AllIcons.FileTypes.Text`           |
| `fileTypes.idea`          | IntelliJ          | `AllIcons.FileTypes.Idea`           |
| `toolwindows.documentation` | Documentation   | `AllIcons.Toolwindows.Documentation`|
| `toolwindows.todoView`    | Todo              | `AllIcons.Toolwindows.ToolWindowTodoView` |
| `toolwindows.problems`    | Problems          | `AllIcons.Toolwindows.Problems`     |
| `toolwindows.versionControl` | Version Control | `AllIcons.Toolwindows.ToolWindowChanges` |
| `vcs.branch`              | Branch            | `AllIcons.Vcs.Branch`               |
| `debugger.console`        | Debug Console     | `AllIcons.Debugger.Console`         |

If any reference does not resolve in the target SDK (Otter 3 FD), that row is silently dropped via `runCatching`. The implementer should verify the final list compiles and visually scan the picker before merging.

## Resolver

`ProjectFileGroupNode.getGroupIcon()` becomes:

```kotlin
private fun getGroupIcon(): Icon =
    GroupIconCatalog.find(fileGroup.iconKey)?.icon ?: autoDetectIcon()

private fun autoDetectIcon(): Icon {
    // existing logic, unchanged
    val inclusionPatterns = fileGroup.patterns.filter { !it.startsWith("!") }
    if (inclusionPatterns.size == 1) {
        // … unchanged
    }
    return AllIcons.Nodes.Folder
}
```

Three behaviors collapse to one rule: override wins iff `iconKey` is non-null **and** present in the catalog. Existing groups (null), groups with stale keys (no longer in catalog), and never-set keys all auto-detect.

## UI: `GroupIconPickerDialog` (new)

Modal `DialogWrapper` opened from the Edit File Group dialog.

- Center panel: `JBList<GroupIconEntry>` with `layoutOrientation = HORIZONTAL_WRAP`, `visibleRowCount = -1`, fixed cell size ~48×48 px.
- Custom `ListCellRenderer<GroupIconEntry>`: a `JBLabel` with the entry's `icon` centered, no text. Tooltip shows `displayName` on hover. Selected cell uses `UIUtil.getListSelectionBackground(true)`.
- Default OK/Cancel buttons (`DialogWrapper` defaults).
- Constructor: `GroupIconPickerDialog(project: Project?, initialKey: String?)`.
- Result accessor: `fun getSelectedKey(): String?` — returns the key of the selected entry (never null when OK is pressed because the list always has a selection; we pre-select the first entry if `initialKey` is null or unresolved).

Estimated size: ~80 lines of Swing.

## UI: `ProjectFileGroupDialog` changes

Add one new row between the group-name field and the patterns section header:

```
[ Icon: ]  [16×16 preview]   "(Auto-detected)" or display name   [ Choose… ] [ Reset to auto ]
```

- New private state: `private var currentIconKey: String? = null`, seeded in `init` from `existingGroup?.iconKey`.
- New private function `refreshIconRow()` updates the preview label's icon and text from `currentIconKey` via the catalog. The "Reset to auto" button is enabled iff `currentIconKey != null`.
- `Choose…` action: opens `GroupIconPickerDialog(null, currentIconKey)`. If `showAndGet()` returns true, set `currentIconKey = dialog.getSelectedKey()` and call `refreshIconRow()`.
- `Reset to auto` action: set `currentIconKey = null`, call `refreshIconRow()`.
- `getProjectFileGroup()` writes `currentIconKey` into the returned `ProjectFileGroup`.

i18n keys (added to `AndroidViewBundle.properties`):

- `dialog.ProjectFileGroup.Icon.label` → `Icon:`
- `dialog.ProjectFileGroup.Icon.choose` → `Choose…`
- `dialog.ProjectFileGroup.Icon.reset` → `Reset to auto`
- `dialog.ProjectFileGroup.Icon.autoDetected` → `(Auto-detected)`
- `dialog.GroupIconPicker.title` → `Pick Group Icon`

## UI: settings table icon column

`AndroidViewSettingsConfigurable` currently shows a 2-column table (Group Name, Patterns). Add a leading 24-px-wide icon column (column 0) that renders the resolved icon for each group:

- `ProjectFileGroupsTableModel.getColumnCount() = 3`.
- Column 0 has empty header text; `getColumnClass(0) = Icon::class.java`; `getValueAt(row, 0)` returns `GroupIconCatalog.find(groups[row].iconKey)?.icon` or the auto-detected icon.
- The `JBTable` uses a default `Icon` renderer (already supplied by `JBTable`) so no custom renderer is required.
- Set the column's `preferredWidth` and `maxWidth` to ~24 px in `createGroupsTablePanel()`.

This is the smallest possible change to make the override visible at-a-glance from the settings page.

## Tests

New: `GroupIconCatalogTest`
- `find(null)` returns null
- `find("does.not.exist")` returns null
- `find` of every key in `entries` returns the matching entry
- `entries` is non-empty (sanity check that `runCatching` didn't silently drop everything)

New (or extension of an existing test if there is one for the node): `ProjectFileGroupNodeIconTest`
- `iconKey = null` → resolver returns the auto-detected icon for the patterns
- Valid `iconKey` → resolver returns `GroupIconCatalog.find(key).icon`
- Unknown `iconKey` (e.g. `"deleted.icon"`) → resolver falls back to auto-detect

UI/Swing tests are intentionally not added — there is no Swing test infrastructure in this repo today and Issue #31 doesn't justify setting that up.

## Risks and mitigations

| Risk | Mitigation |
|---|---|
| `AllIcons.X.Y` member is missing/renamed in target SDK | Each catalog entry built inside `runCatching` and filtered out on failure. Plugin still loads; that icon just isn't pickable. |
| User exports settings on a newer IDE, imports on an older IDE that lacks the chosen icon | Resolver falls back to auto-detect. User sees their old icon, no error. |
| Future contributor adds an entry without verifying it compiles in the target SDK | Catalog entry-list test (`entries.isNotEmpty()`) plus visual review during implementation. |

## Out of scope follow-ups (not this PR)

- Per-pattern icons (one icon when 2+ patterns are matched by different file types).
- Custom user-supplied SVG/PNG icons.
- Searchable picker over the full `AllIcons` set.
- Reordering / favorites / recently-used in the picker.
