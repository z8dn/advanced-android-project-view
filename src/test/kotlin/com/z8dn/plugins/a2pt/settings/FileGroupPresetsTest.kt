package com.z8dn.plugins.a2pt.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileGroupPresetsTest {

    private fun preset(name: String, vararg groups: ProjectFileGroup) =
        FileGroupPreset(name, groups.toList())

    private fun group(name: String, vararg patterns: String) =
        ProjectFileGroup(name, patterns.toMutableList())

    // region preset content

    @Test
    fun `every shipped preset has at least one group`() {
        FileGroupPresets.ALL.forEach { p ->
            assertTrue("preset '${p.displayName}' should have groups", p.groups.isNotEmpty())
        }
    }

    @Test
    fun `every group has a name and at least one inclusion pattern`() {
        FileGroupPresets.ALL.forEach { p ->
            p.groups.forEach { g ->
                assertTrue("group name should not be blank in '${p.displayName}'", g.groupName.isNotBlank())
                assertTrue("group '${g.groupName}' should have patterns", g.patterns.isNotEmpty())
                assertTrue(
                    "group '${g.groupName}' should have at least one inclusion pattern",
                    g.patterns.any { !it.startsWith("!") }
                )
            }
        }
    }

    @Test
    fun `group names are unique within each preset`() {
        FileGroupPresets.ALL.forEach { p ->
            val names = p.groups.map { it.groupName.lowercase() }
            assertEquals("group names should be unique in '${p.displayName}'", names.size, names.toSet().size)
        }
    }

    // endregion

    // region merge

    @Test
    fun `replace all discards existing groups`() {
        val existing = listOf(group("Existing", "*.kt"))
        val p = preset("P", group("A", "*.md"))

        val result = FileGroupPresets.merge(existing, p, PresetMergeMode.REPLACE_ALL)

        assertEquals(listOf("A"), result.map { it.groupName })
    }

    @Test
    fun `append skips groups whose name already exists case-insensitively`() {
        val existing = listOf(group("Docs", "*.txt"))
        val p = preset("P", group("docs", "*.md"), group("New", "*.kt"))

        val result = FileGroupPresets.merge(existing, p, PresetMergeMode.APPEND_SKIP_DUPLICATES)

        assertEquals(listOf("Docs", "New"), result.map { it.groupName })
        // existing 'Docs' patterns preserved, not overwritten
        assertEquals(mutableListOf("*.txt"), result.first { it.groupName == "Docs" }.patterns)
    }

    @Test
    fun `overwrite replaces patterns of duplicate names and appends the rest`() {
        val existing = listOf(group("Docs", "*.txt"))
        val p = preset("P", group("docs", "*.md"), group("New", "*.kt"))

        val result = FileGroupPresets.merge(existing, p, PresetMergeMode.OVERWRITE_DUPLICATES)

        assertEquals(listOf("Docs", "New"), result.map { it.groupName })
        assertEquals(mutableListOf("*.md"), result.first { it.groupName == "Docs" }.patterns)
    }

    @Test
    fun `merge does not mutate the source lists`() {
        val existing = listOf(group("Docs", "*.txt"))
        val p = preset("P", group("docs", "*.md"))

        FileGroupPresets.merge(existing, p, PresetMergeMode.OVERWRITE_DUPLICATES)

        assertEquals(mutableListOf("*.txt"), existing.first().patterns)
        assertEquals(mutableListOf("*.md"), p.groups.first().patterns)
    }

    // endregion
}
