package com.z8dn.plugins.a2pt.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidViewNodeUtilsTest {

    // region backward compatibility

    @Test
    fun `glob pattern matches by filename`() {
        assertTrue(match("README.md", "README.md", listOf("*.md")))
    }

    @Test
    fun `glob pattern is case-insensitive`() {
        assertTrue(match("README.MD", "README.MD", listOf("*.md")))
    }

    @Test
    fun `exact filename match`() {
        assertTrue(match("LICENSE", "LICENSE", listOf("LICENSE")))
    }

    @Test
    fun `non-matching filename returns false`() {
        assertFalse(match("build.gradle.kts", "build.gradle.kts", listOf("*.md")))
    }

    // endregion

    // region path-based patterns

    @Test
    fun `path pattern matches file in correct directory`() {
        assertTrue(match("guide.md", "docs/guide.md", listOf("docs/*.md")))
    }

    @Test
    fun `path pattern does not match file in wrong directory`() {
        assertFalse(match("guide.md", "archive/guide.md", listOf("docs/*.md")))
    }

    @Test
    fun `path pattern does not match root-level file`() {
        assertFalse(match("guide.md", "guide.md", listOf("docs/*.md")))
    }

    // endregion

    // region exclusion patterns

    @Test
    fun `exclusion pattern removes matching file`() {
        assertFalse(match("notes.md", "archive/notes.md", listOf("*.md", "!archive/*.md")))
    }

    @Test
    fun `exclusion pattern does not affect non-matching file`() {
        assertTrue(match("guide.md", "docs/guide.md", listOf("*.md", "!archive/*.md")))
    }

    @Test
    fun `filename exclusion removes file regardless of directory`() {
        assertFalse(match("claude.md", "claude.md", listOf("*.md", "!claude.md")))
    }

    @Test
    fun `filename exclusion does not affect other md files`() {
        assertTrue(match("README.md", "README.md", listOf("*.md", "!claude.md")))
    }

    @Test
    fun `exclusion without inclusion includes nothing`() {
        // inclusions is empty -> included = true, but then excluded by !*.md
        assertFalse(match("README.md", "README.md", listOf("!*.md")))
    }

    // endregion

    // region multiple patterns

    @Test
    fun `multiple inclusions match any`() {
        assertTrue(match("LICENSE", "LICENSE", listOf("*.md", "LICENSE")))
        assertTrue(match("README.md", "README.md", listOf("*.md", "LICENSE")))
    }

    @Test
    fun `inclusion and path-based exclusion combined`() {
        val patterns = listOf("*.md", "!archive/*.md")
        assertTrue(match("README.md", "README.md", patterns))
        assertTrue(match("guide.md", "docs/guide.md", patterns))
        assertFalse(match("notes.md", "archive/notes.md", patterns))
    }

    // endregion

    // region glob validation

    @Test
    fun `isValidGlob accepts well-formed patterns`() {
        assertTrue(AndroidViewNodeUtils.isValidGlob("*.md"))
        assertTrue(AndroidViewNodeUtils.isValidGlob("docs/**"))
        assertTrue(AndroidViewNodeUtils.isValidGlob("**/*.kt"))
        assertTrue(AndroidViewNodeUtils.isValidGlob("LICENSE"))
        assertTrue(AndroidViewNodeUtils.isValidGlob("*.{md,txt}"))
    }

    @Test
    fun `isValidGlob rejects malformed patterns`() {
        assertFalse(AndroidViewNodeUtils.isValidGlob("*.{md"))
        assertFalse(AndroidViewNodeUtils.isValidGlob("[abc"))
        assertFalse(AndroidViewNodeUtils.isValidGlob("a\\"))
    }

    @Test
    fun `matcher cache returns consistent results across repeated calls`() {
        repeat(3) {
            assertTrue(match("README.md", "README.md", listOf("*.md")))
            assertFalse(match("build.gradle.kts", "build.gradle.kts", listOf("*.md")))
        }
    }

    @Test
    fun `invalid pattern is cached without throwing`() {
        // ConcurrentHashMap forbids null values: an invalid glob must cache a sentinel.
        repeat(3) {
            assertFalse(AndroidViewNodeUtils.isValidGlob("*.{md"))
            assertFalse(match("README.md", "README.md", listOf("*.{md")))
        }
    }

    @Test
    fun `matching against an invalid pattern never matches`() {
        assertFalse(match("README.md", "README.md", listOf("[abc")))
    }

    // endregion

    // region single-pattern matching

    @Test
    fun `matchesSinglePattern dispatches to relative path when pattern has a slash`() {
        assertTrue(AndroidViewNodeUtils.matchesSinglePattern("guide.md", "docs/guide.md", "docs/*.md"))
        assertFalse(AndroidViewNodeUtils.matchesSinglePattern("guide.md", "archive/guide.md", "docs/*.md"))
    }

    @Test
    fun `matchesSinglePattern dispatches to filename when pattern has no slash`() {
        assertTrue(AndroidViewNodeUtils.matchesSinglePattern("guide.md", "archive/guide.md", "*.md"))
        assertTrue(AndroidViewNodeUtils.matchesSinglePattern("LICENSE", "sub/LICENSE", "LICENSE"))
        assertFalse(AndroidViewNodeUtils.matchesSinglePattern("guide.md", "docs/guide.md", "*.kt"))
    }

    @Test
    fun `matchesSinglePattern is case-insensitive`() {
        assertTrue(AndroidViewNodeUtils.matchesSinglePattern("README.MD", "README.MD", "*.md"))
    }

    @Test
    fun `matchesSinglePattern ignores exclusion semantics`() {
        // The caller strips "!" — the raw "!x" pattern is just a literal name here.
        assertFalse(AndroidViewNodeUtils.matchesSinglePattern("README.md", "README.md", "!*.md"))
    }

    // endregion

    private fun match(fileName: String, relativePath: String, patterns: List<String>): Boolean {
        return AndroidViewNodeUtils.matchesPatterns(fileName, relativePath, patterns)
    }
}
