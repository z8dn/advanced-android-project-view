package com.z8dn.plugins.a2pt.settings

import com.z8dn.plugins.a2pt.settings.ProjectFileGroupsSerializer.FailureReason
import com.z8dn.plugins.a2pt.settings.ProjectFileGroupsSerializer.GroupProblem
import com.z8dn.plugins.a2pt.settings.ProjectFileGroupsSerializer.ImportResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectFileGroupsSerializerTest {

    // region round trip

    @Test
    fun `round trip preserves groups`() {
        val groups = listOf(
            group("Docs", "*.md", "!archive/*.md"),
            group("AI Rules", "CLAUDE.md", "AGENTS.md")
        )
        assertEquals(groups, success(ProjectFileGroupsSerializer.toJson(groups)))
    }

    @Test
    fun `order of groups and patterns is preserved`() {
        val groups = listOf(group("Z", "z", "a", "m"), group("A", "b"), group("M", "c"))
        val back = success(ProjectFileGroupsSerializer.toJson(groups))
        assertEquals(listOf("Z", "A", "M"), back.map { it.groupName })
        assertEquals(listOf("z", "a", "m"), back[0].patterns)
    }

    @Test
    fun `exclusion patterns are preserved`() {
        val back = success(ProjectFileGroupsSerializer.toJson(listOf(group("Docs", "*.md", "!archive/*.md"))))
        assertEquals(listOf("*.md", "!archive/*.md"), back.single().patterns)
    }

    @Test
    fun `output is versioned pretty printed json`() {
        val json = ProjectFileGroupsSerializer.toJson(listOf(group("Docs", "*.md")))
        assertTrue(json.contains("\"version\": 1"))
        assertTrue(json.contains("\n  \"groups\""))
    }

    @Test
    fun `empty groups list is valid`() {
        assertEquals(emptyList<ProjectFileGroup>(), success(ProjectFileGroupsSerializer.toJson(emptyList())))
    }

    // endregion

    // region rejection

    @Test
    fun `malformed json is rejected`() {
        assertTrue(failure("{ not json") is FailureReason.MalformedJson)
        assertTrue(failure("") is FailureReason.MalformedJson)
    }

    @Test
    fun `non object top level is rejected`() {
        assertTrue(failure("[1, 2]") is FailureReason.InvalidStructure)
    }

    @Test
    fun `missing version is rejected`() {
        assertEquals(FailureReason.MissingVersion, failure("""{"groups": []}"""))
    }

    @Test
    fun `invalid version is rejected`() {
        assertEquals(FailureReason.MissingVersion, failure("""{"version": "1", "groups": []}"""))
        assertEquals(FailureReason.MissingVersion, failure("""{"version": 1.5, "groups": []}"""))
        assertEquals(FailureReason.MissingVersion, failure("""{"version": 0, "groups": []}"""))
    }

    @Test
    fun `newer version is rejected`() {
        assertEquals(FailureReason.NewerVersion(2), failure("""{"version": 2, "groups": []}"""))
    }

    @Test
    fun `missing groups array is rejected`() {
        assertTrue(failure("""{"version": 1}""") is FailureReason.InvalidStructure)
    }

    @Test
    fun `unknown fields are ignored`() {
        val json = """
            {"version": 1, "extra": true,
             "groups": [{"name": "Docs", "patterns": ["*.md"], "color": "red"}]}
        """
        assertEquals(listOf(group("Docs", "*.md")), success(json))
    }

    @Test
    fun `blank name is rejected`() {
        val reason = failure(file("""{"name": "  ", "patterns": ["*.md"]}"""))
        assertEquals(FailureReason.InvalidGroup(0, "  ", GroupProblem.EmptyName), reason)
    }

    @Test
    fun `group without inclusion pattern is rejected`() {
        val reason = failure(file("""{"name": "A", "patterns": ["*.md"]}, {"name": "B", "patterns": ["!*.md"]}"""))
        assertEquals(FailureReason.InvalidGroup(1, "B", GroupProblem.NoInclusionPattern), reason)
    }

    @Test
    fun `group without patterns is rejected`() {
        val reason = failure(file("""{"name": "A", "patterns": []}"""))
        assertEquals(FailureReason.InvalidGroup(0, "A", GroupProblem.NoPatterns), reason)
    }

    @Test
    fun `blank pattern is rejected`() {
        val reason = failure(file("""{"name": "A", "patterns": ["*.md", " "]}"""))
        assertEquals(FailureReason.InvalidGroup(0, "A", GroupProblem.BlankPattern), reason)
    }

    @Test
    fun `invalid glob is rejected`() {
        val reason = failure(file("""{"name": "A", "patterns": ["[abc"]}"""))
        assertEquals(FailureReason.InvalidGroup(0, "A", GroupProblem.InvalidGlob("[abc")), reason)
    }

    @Test
    fun `invalid glob behind an exclusion prefix is rejected`() {
        val reason = failure(file("""{"name": "A", "patterns": ["*.md", "![abc"]}"""))
        assertEquals(FailureReason.InvalidGroup(0, "A", GroupProblem.InvalidGlob("![abc")), reason)
    }

    @Test
    fun `duplicate names in file are rejected case-insensitively`() {
        val reason = failure(file("""{"name": "Docs", "patterns": ["*.md"]}, {"name": " docs ", "patterns": ["*.txt"]}"""))
        assertTrue(reason is FailureReason.DuplicateName)
    }

    @Test
    fun `one bad group rejects the whole file`() {
        val result = ProjectFileGroupsSerializer.fromJson(
            file("""{"name": "Good", "patterns": ["*.md"]}, {"name": "", "patterns": ["*.md"]}""")
        )
        assertTrue(result is ImportResult.Failure)
    }

    @Test
    fun `names and patterns are trimmed`() {
        val back = success(file("""{"name": "  Docs ", "patterns": [" *.md ", " !a/*.md"]}"""))
        assertEquals(listOf(group("Docs", "*.md", "!a/*.md")), back)
    }

    // endregion

    // region merge

    @Test
    fun `merge replaces same named group in place and appends new ones`() {
        val existing = listOf(group("A", "a"), group("B", "b"), group("C", "c"))
        val imported = listOf(group("New", "n"), group("b", "b2"))
        val merged = ProjectFileGroupsSerializer.merge(existing, imported)
        assertEquals(listOf(group("A", "a"), group("b", "b2"), group("C", "c"), group("New", "n")), merged)
    }

    @Test
    fun `merge into empty list appends everything`() {
        val imported = listOf(group("A", "a"), group("B", "b"))
        assertEquals(imported, ProjectFileGroupsSerializer.merge(emptyList(), imported))
    }

    @Test
    fun `merge of nothing keeps existing`() {
        val existing = listOf(group("A", "a"))
        assertEquals(existing, ProjectFileGroupsSerializer.merge(existing, emptyList()))
    }

    // endregion

    private fun group(name: String, vararg patterns: String) =
        ProjectFileGroup(name, patterns.toMutableList())

    private fun file(groups: String) = """{"version": 1, "groups": [$groups]}"""

    private fun success(json: String): List<ProjectFileGroup> {
        val result = ProjectFileGroupsSerializer.fromJson(json)
        assertTrue("expected success but was $result", result is ImportResult.Success)
        return (result as ImportResult.Success).groups
    }

    private fun failure(json: String): FailureReason {
        val result = ProjectFileGroupsSerializer.fromJson(json)
        assertTrue("expected failure but was $result", result is ImportResult.Failure)
        return (result as ImportResult.Failure).reason
    }
}
