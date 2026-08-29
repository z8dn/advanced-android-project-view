package com.z8dn.plugins.a2pt.settings

import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.z8dn.plugins.a2pt.utils.AndroidViewNodeUtils
import com.z8dn.plugins.a2pt.utils.ProjectFileDisplayUtils

/**
 * How a single pattern behaved against the candidate pool.
 */
enum class PatternKind {
    /** An inclusion that matched at least one file. */
    MATCHED,

    /** An inclusion that compiled fine but matched nothing. */
    EMPTY,

    /** An exclusion (`!`-prefixed). Its count is reported as a negative number. */
    NEGATION,

    /** A pattern that does not compile as a glob, so it can never match anything. */
    INVALID,

    /** An empty row — the dialog's `+` adds one before the user has typed anything. */
    BLANK
}

/**
 * The per-pattern result shown in the dialog's `Files` column.
 * [count] is null when the pattern is [PatternKind.INVALID] — there is no meaningful number.
 */
data class PatternStat(val pattern: String, val count: Int?, val kind: PatternKind)

/**
 * One file that the draft group's patterns produce.
 * [excludedBy] is the `!pattern` that removed it, or null if the file is included.
 */
data class PreviewRow(
    val file: VirtualFile,
    val relativePath: String,
    val qualifier: String,
    val excludedBy: String?
)

/**
 * @param rows       included rows first (capped at [ProjectFileGroupPreview.ROW_CAP]), then
 *                   excluded rows (also capped). Callers filter on [PreviewRow.excludedBy].
 * @param matched    exact number of included files, ignoring the row cap
 * @param excluded   exact number of files removed by exclusion patterns
 * @param stats      one entry per draft pattern, in the order the patterns were given
 * @param truncated  true when [matched] exceeds the row cap
 */
data class PreviewResult(
    val rows: List<PreviewRow>,
    val matched: Int,
    val excluded: Int,
    val stats: List<PatternStat>,
    val truncated: Boolean
) {
    val includedRows: List<PreviewRow> get() = rows.filter { it.excludedBy == null }
}

/**
 * Computes exactly what a [ProjectFileGroup]'s node will contain in the project tree, so a
 * mistyped pattern is visible in the dialog rather than as a silently missing group.
 *
 * Exactness rests on three things, all handled here:
 *
 * 1. **The candidate pool is shared across groups.** `collectMatchingFiles` derives its
 *    directory-prefix scan from the union of *every* group's patterns, so a group whose only
 *    pattern is `*.md` still shows `docs/adr/0001.md` — but only because some other group
 *    carries a recursive `docs` pattern. The pool must therefore be built from the sibling
 *    groups' inclusions
 *    unioned with the draft's, i.e. the edited group is *substituted*, never added. That is
 *    exactly the union that will exist after OK is pressed.
 * 2. **The PSI gate.** `ProjectFileGroupNode` skips any file `PsiManager.findFile` returns null
 *    for, so the preview must apply the same check.
 * 3. **Display mode.** The above describes top-level group mode
 *    (`showProjectFilesInModule = false`), which is what the preview renders.
 */
object ProjectFileGroupPreview {

    /** Rendering cap. [PreviewResult.matched] stays exact regardless. */
    const val ROW_CAP = 200

    /**
     * Must be called inside a read action: this touches the VFS and PSI.
     *
     * @param draftPatterns      the patterns currently in the dialog, exclusions included
     * @param siblingInclusions  inclusion patterns of every *other* group. The caller supplies
     *                           these; this object never reads [AndroidViewSettings], so the
     *                           preview reflects unsaved edits rather than persisted state.
     */
    fun compute(
        project: Project,
        draftPatterns: List<String>,
        siblingInclusions: List<String>
    ): PreviewResult {
        val draftInclusions = draftPatterns.filter { !it.startsWith("!") && it.isNotBlank() }
        if (draftInclusions.isEmpty()) {
            // Nothing can be included, but the patterns still deserve their per-row verdict.
            return PreviewResult(emptyList(), 0, 0, computeStats(draftPatterns, emptyList()), false)
        }

        // Rule 1: substitute the draft into the union, never add to it.
        val poolPatterns = (siblingInclusions.filter { !it.startsWith("!") } + draftInclusions).distinct()
        val pool = AndroidViewNodeUtils.collectMatchingFiles(project, poolPatterns)

        val psiManager = PsiManager.getInstance(project)
        val candidates = pool.filter { (file, relativePath) ->
            AndroidViewNodeUtils.matchesPatterns(file.name, relativePath, draftInclusions) &&
                psiManager.findFile(file) != null // Rule 2
        }

        val rows = candidates.map { (file, relativePath) ->
            PreviewRow(
                file = file,
                relativePath = relativePath,
                qualifier = qualifierFor(project, file, relativePath),
                excludedBy = findExcludedBy(file.name, relativePath, draftPatterns)
            )
        }.sortedWith(ROW_ORDER)

        val included = rows.filter { it.excludedBy == null }
        val excluded = rows.filter { it.excludedBy != null }

        return PreviewResult(
            rows = included.take(ROW_CAP) + excluded.take(ROW_CAP),
            matched = included.size,
            excluded = excluded.size,
            stats = computeStats(draftPatterns, candidates.map { it.first.name to it.second }),
            truncated = included.size > ROW_CAP
        )
    }

    /**
     * Mirrors `ProjectFileGroupNode.getChildren` so the preview row and the tree node read
     * identically by construction rather than by coincidence.
     */
    private fun qualifierFor(project: Project, file: VirtualFile, relativePath: String): String {
        val module = ModuleUtilCore.findModuleForFile(file, project)
        return if (module != null) {
            ProjectFileDisplayUtils.generateDisplayName(file, module)
        } else {
            file.parent?.name ?: relativePath
        }
    }

    /** The tree sorts alphabetically; match it so the two lists line up one to one. */
    private val ROW_ORDER: Comparator<PreviewRow> =
        compareBy(String.CASE_INSENSITIVE_ORDER) { row: PreviewRow -> row.file.name }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { row -> row.relativePath }

    /**
     * The first exclusion pattern that removes this file, or null if none does.
     *
     * Pure — takes names rather than files so it can be tested without a VFS fixture.
     */
    fun findExcludedBy(fileName: String, relativePath: String, patterns: List<String>): String? =
        patterns.firstOrNull {
            it.startsWith("!") &&
                AndroidViewNodeUtils.matchesSinglePattern(fileName, relativePath, it.removePrefix("!"))
        }

    /**
     * One [PatternStat] per pattern, in the given order.
     *
     * Inclusion counts are taken *before* exclusions are applied, so a pattern's number
     * answers "what does this row contribute?" rather than "what survived?". Exclusion counts
     * are negative: they report how many files the pattern takes away.
     *
     * Pure — [includedPool] is a list of (fileName, relativePath) pairs.
     */
    fun computeStats(patterns: List<String>, includedPool: List<Pair<String, String>>): List<PatternStat> =
        patterns.map { pattern ->
            val isExclusion = pattern.startsWith("!")
            val body = pattern.removePrefix("!")

            if (body.isBlank()) {
                return@map PatternStat(pattern, null, PatternKind.BLANK)
            }
            if (!AndroidViewNodeUtils.isValidGlob(body)) {
                return@map PatternStat(pattern, null, PatternKind.INVALID)
            }

            val hits = includedPool.count { (fileName, relativePath) ->
                AndroidViewNodeUtils.matchesSinglePattern(fileName, relativePath, body)
            }

            when {
                isExclusion -> PatternStat(pattern, -hits, PatternKind.NEGATION)
                hits == 0 -> PatternStat(pattern, 0, PatternKind.EMPTY)
                else -> PatternStat(pattern, hits, PatternKind.MATCHED)
            }
        }
}
