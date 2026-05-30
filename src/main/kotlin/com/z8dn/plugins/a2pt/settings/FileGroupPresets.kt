package com.z8dn.plugins.a2pt.settings

import com.z8dn.plugins.a2pt.AndroidViewBundle

/**
 * A named, predefined set of [ProjectFileGroup]s for a common project stack.
 *
 * Presets ship with the plugin and can be loaded into the Custom File Groups list from the
 * settings page. Loading a preset produces ordinary [ProjectFileGroup] instances that remain
 * freely editable afterwards.
 */
data class FileGroupPreset(
    val displayName: String,
    val groups: List<ProjectFileGroup>
)

/**
 * How a preset should be merged into the existing list of file groups.
 */
enum class PresetMergeMode {
    /** Add the preset's groups, skipping any whose name already exists (case-insensitive). */
    APPEND_SKIP_DUPLICATES,

    /** Add the preset's groups, replacing the patterns of any existing group with the same name. */
    OVERWRITE_DUPLICATES,

    /** Discard the existing groups entirely and use only the preset's groups. */
    REPLACE_ALL
}

/**
 * Built-in file-group presets for common stacks.
 */
object FileGroupPresets {

    private fun group(name: String, vararg patterns: String): ProjectFileGroup =
        ProjectFileGroup(groupName = name, patterns = patterns.toMutableList())

    /** Standard Android project structure. */
    private fun androidPreset(): FileGroupPreset = FileGroupPreset(
        displayName = AndroidViewBundle.message("preset.Android.text"),
        groups = listOf(
            group(
                "Gradle Scripts",
                "build.gradle", "build.gradle.kts",
                "settings.gradle", "settings.gradle.kts",
                "gradle.properties", "libs.versions.toml", "*.versions.toml"
            ),
            group(
                "Android Config",
                "AndroidManifest.xml", "proguard-rules.pro", "consumer-rules.pro", "*.pro"
            ),
            group(
                "Documentation",
                "*.md", "LICENSE", "NOTICE", "*.txt"
            )
        )
    )

    /** Kotlin Multiplatform (KMP) project structure. */
    private fun kmpPreset(): FileGroupPreset = FileGroupPreset(
        displayName = AndroidViewBundle.message("preset.Kmp.text"),
        groups = listOf(
            group(
                "Gradle Scripts",
                "*.gradle.kts", "gradle.properties", "libs.versions.toml"
            ),
            group(
                "Native Configs",
                "*.def", "*.podspec", "Podfile", "*.xcconfig"
            ),
            group(
                "Platform Metadata",
                "*.toml", "*.properties"
            )
        )
    )

    /** Compose Multiplatform project structure. */
    private fun composePreset(): FileGroupPreset = FileGroupPreset(
        displayName = AndroidViewBundle.message("preset.Compose.text"),
        groups = listOf(
            group(
                "Compose Config",
                "*.gradle.kts", "compose.*", "gradle.properties"
            ),
            group(
                "Compose Resources",
                "composeResources/*", "*.xml", "*.cdr"
            )
        )
    )

    /** All presets shipped with the plugin, in display order. */
    val ALL: List<FileGroupPreset>
        get() = listOf(androidPreset(), kmpPreset(), composePreset())

    /**
     * Merges [preset] into [existing] according to [mode], returning a new list.
     * Group name comparison is case-insensitive.
     */
    fun merge(
        existing: List<ProjectFileGroup>,
        preset: FileGroupPreset,
        mode: PresetMergeMode
    ): List<ProjectFileGroup> {
        fun copy(g: ProjectFileGroup) = ProjectFileGroup(g.groupName, g.patterns.toMutableList())

        return when (mode) {
            PresetMergeMode.REPLACE_ALL -> preset.groups.map { copy(it) }

            PresetMergeMode.APPEND_SKIP_DUPLICATES -> {
                val existingNames = existing.map { it.groupName.lowercase() }.toMutableSet()
                val result = existing.map { copy(it) }.toMutableList()
                preset.groups.forEach { g ->
                    if (existingNames.add(g.groupName.lowercase())) {
                        result.add(copy(g))
                    }
                }
                result
            }

            PresetMergeMode.OVERWRITE_DUPLICATES -> {
                val result = existing.map { copy(it) }.toMutableList()
                preset.groups.forEach { g ->
                    val index = result.indexOfFirst { it.groupName.equals(g.groupName, ignoreCase = true) }
                    if (index >= 0) {
                        result[index] = copy(g)
                    } else {
                        result.add(copy(g))
                    }
                }
                result
            }
        }
    }
}
