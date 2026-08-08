package com.z8dn.plugins.a2pt.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Covers the parts of the preview that are pure functions over (fileName, relativePath) pairs.
 * The pool/PSI/qualifier step needs the platform and is exercised by the manual pass instead.
 */
class ProjectFileGroupPreviewTest {

    private val pool = listOf(
        "README.md" to "README.md",
        "guide.md" to "docs/guide.md",
        "notes.md" to "docs/archive/notes.md",
        "LICENSE" to "LICENSE",
        "build.gradle.kts" to "build.gradle.kts"
    )

    // region inclusion counts

    @Test
    fun `inclusion reports how many pool entries it matches`() {
        val stats = ProjectFileGroupPreview.computeStats(listOf("*.md"), pool)

        assertEquals(1, stats.size)
        assertEquals(3, stats[0].count)
        assertEquals(PatternKind.MATCHED, stats[0].kind)
    }

    @Test
    fun `inclusion matching nothing is EMPTY with a zero count`() {
        val stats = ProjectFileGroupPreview.computeStats(listOf("*.mdown"), pool)

        assertEquals(0, stats[0].count)
        assertEquals(PatternKind.EMPTY, stats[0].kind)
    }

    @Test
    fun `stats are returned one per pattern in the given order`() {
        val stats = ProjectFileGroupPreview.computeStats(listOf("LICENSE", "*.md", "*.mdown"), pool)

        assertEquals(listOf("LICENSE", "*.md", "*.mdown"), stats.map { it.pattern })
        assertEquals(listOf(1, 3, 0), stats.map { it.count })
    }

    @Test
    fun `path-based inclusion counts only files under that path`() {
        val stats = ProjectFileGroupPreview.computeStats(listOf("docs/*.md"), pool)

        assertEquals(1, stats[0].count)
        assertEquals(PatternKind.MATCHED, stats[0].kind)
    }

    // endregion

    // region exclusion counts

    @Test
    fun `exclusion reports a negative count of what it removes`() {
        val stats = ProjectFileGroupPreview.computeStats(listOf("*.md", "!docs/archive/*.md"), pool)

        assertEquals(3, stats[0].count)
        assertEquals(-1, stats[1].count)
        assertEquals(PatternKind.NEGATION, stats[1].kind)
    }

    @Test
    fun `exclusion that removes nothing is still a NEGATION`() {
        val stats = ProjectFileGroupPreview.computeStats(listOf("*.md", "!*.draft"), pool)

        assertEquals(0, stats[1].count)
        assertEquals(PatternKind.NEGATION, stats[1].kind)
    }

    @Test
    fun `inclusion counts are taken before exclusions are applied`() {
        // "*.md" still reports 3 even though one of them is excluded on the next row.
        val stats = ProjectFileGroupPreview.computeStats(listOf("*.md", "!notes.md"), pool)

        assertEquals(3, stats[0].count)
        assertEquals(-1, stats[1].count)
    }

    // endregion

    // region invalid patterns

    @Test
    fun `invalid inclusion is classified INVALID with no count`() {
        val stats = ProjectFileGroupPreview.computeStats(listOf("*.{md"), pool)

        assertEquals(PatternKind.INVALID, stats[0].kind)
        assertNull(stats[0].count)
    }

    @Test
    fun `invalid exclusion is classified INVALID rather than NEGATION`() {
        val stats = ProjectFileGroupPreview.computeStats(listOf("*.md", "![abc"), pool)

        assertEquals(PatternKind.MATCHED, stats[0].kind)
        assertEquals(PatternKind.INVALID, stats[1].kind)
        assertNull(stats[1].count)
    }

    @Test
    fun `a blank row is BLANK rather than EMPTY or INVALID`() {
        // The dialog's "+" adds an empty row; it must not be flagged before anything is typed,
        // and it must still occupy a slot so stats stay index-aligned with the table.
        val stats = ProjectFileGroupPreview.computeStats(listOf("*.md", "", "   "), pool)

        assertEquals(3, stats.size)
        assertEquals(PatternKind.MATCHED, stats[0].kind)
        assertEquals(PatternKind.BLANK, stats[1].kind)
        assertEquals(PatternKind.BLANK, stats[2].kind)
        assertNull(stats[1].count)
    }

    @Test
    fun `an empty pool makes every valid inclusion EMPTY`() {
        val stats = ProjectFileGroupPreview.computeStats(listOf("*.md", "!*.draft"), emptyList())

        assertEquals(PatternKind.EMPTY, stats[0].kind)
        assertEquals(PatternKind.NEGATION, stats[1].kind)
        assertEquals(0, stats[1].count)
    }

    // endregion

    // region exclusion attribution

    @Test
    fun `findExcludedBy names the pattern that removed the file`() {
        val patterns = listOf("*.md", "!docs/archive/*.md")

        assertEquals(
            "!docs/archive/*.md",
            ProjectFileGroupPreview.findExcludedBy("notes.md", "docs/archive/notes.md", patterns)
        )
    }

    @Test
    fun `findExcludedBy returns null for an included file`() {
        val patterns = listOf("*.md", "!docs/archive/*.md")

        assertNull(ProjectFileGroupPreview.findExcludedBy("guide.md", "docs/guide.md", patterns))
    }

    @Test
    fun `findExcludedBy returns the first matching exclusion when several apply`() {
        val patterns = listOf("*.md", "!*.md", "!docs/archive/*.md")

        assertEquals(
            "!*.md",
            ProjectFileGroupPreview.findExcludedBy("notes.md", "docs/archive/notes.md", patterns)
        )
    }

    @Test
    fun `findExcludedBy ignores inclusion patterns`() {
        assertNull(ProjectFileGroupPreview.findExcludedBy("README.md", "README.md", listOf("*.md")))
    }

    // endregion
}
