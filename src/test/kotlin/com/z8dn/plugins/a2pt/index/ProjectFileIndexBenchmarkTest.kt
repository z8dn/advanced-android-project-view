package com.z8dn.plugins.a2pt.index

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.VfsTestUtil
import com.z8dn.plugins.a2pt.settings.AndroidViewSettings
import com.z8dn.plugins.a2pt.settings.ProjectFileGroup
import com.z8dn.plugins.a2pt.utils.AndroidViewNodeUtils
import java.io.File

/**
 * Measures the refactor rather than asserting it.
 *
 * The comparison is against [legacyFilesForModule] below — a reference implementation of the
 * pre-refactor `getProjectFilesForModule`, kept here so this measures real code rather than a
 * description of it. Both paths are run over the same fixture and their **output is asserted
 * identical** first: a cheaper answer that is also a different answer proves nothing.
 *
 * What is asserted is *work done*, which is deterministic and therefore safe in CI:
 *
 * - full project sweeps
 * - files visited during those sweeps
 * - `moduleContainsFile` calls
 *
 * Wall-clock is printed but never asserted. It depends on the machine, and a timing assertion
 * would be a flaky test pretending to be a benchmark.
 */
class ProjectFileIndexBenchmarkTest : HeavyPlatformTestCase() {

    private companion object {
        /** Modules in the synthetic project. The old cost is linear in this. */
        const val MODULES = 25

        /** Files directly under each module's content root. */
        const val FILES_PER_MODULE = 8

        /** Of those, how many any group actually claims (`README.md`, `NOTES.md`). */
        const val MATCHING_PER_MODULE = 2
    }

    private val modules = mutableListOf<Module>()
    private var savedGroups: MutableList<ProjectFileGroup> = mutableListOf()

    override fun setUp() {
        super.setUp()
        val settings = AndroidViewSettings.getInstance()
        savedGroups = settings.projectFileGroups
        settings.projectFileGroups = mutableListOf()

        val base = baseDir()
        repeat(MODULES) { i ->
            val dir = "module$i"
            VfsTestUtil.createFile(base, "$dir/build.gradle.kts", "")
            VfsTestUtil.createFile(base, "$dir/README.md", "")
            VfsTestUtil.createFile(base, "$dir/NOTES.md", "")
            repeat(FILES_PER_MODULE - 3) { k -> VfsTestUtil.createFile(base, "$dir/Source$k.kt", "") }

            val module = createModule("module$i")
            PsiTestUtil.addContentRoot(
                module,
                baseDir().findFileByRelativePath(dir) ?: error("missing $dir")
            )
            modules += module
        }
    }

    override fun tearDown() {
        try {
            AndroidViewSettings.getInstance().projectFileGroups = savedGroups
        } finally {
            super.tearDown()
        }
    }

    fun testCachedIndexDoesLessWorkForTheSameAnswer() {
        AndroidViewSettings.getInstance().projectFileGroups =
            mutableListOf(ProjectFileGroup("Docs", mutableListOf("*.md")))

        val index = ProjectFileGroupIndex.getInstance(project)
        index.invalidate()

        // --- one tree build, old algorithm -------------------------------------------------
        val legacy = Counters()
        val legacyStart = System.nanoTime()
        val legacyResult = ReadAction.compute<Map<String, Set<String>>, RuntimeException> {
            modules.associate { it.name to legacyFilesForModule(it, legacy) }
        }
        legacy.elapsedMs = (System.nanoTime() - legacyStart) / 1_000_000.0

        // --- the same tree build, through the index ----------------------------------------
        val cachedStart = System.nanoTime()
        val rebuildsBefore = index.rebuildCount()
        val cachedResult = ReadAction.compute<Map<String, Set<String>>, RuntimeException> {
            modules.associate { module -> module.name to index.filesFor(module).map { it.relative() }.toSet() }
        }
        val coldElapsedMs = (System.nanoTime() - cachedStart) / 1_000_000.0
        val coldSweeps = index.rebuildCount() - rebuildsBefore

        // --- a second tree build with nothing changed, the Gradle-build case ---------------
        val warmStart = System.nanoTime()
        val warmBefore = index.rebuildCount()
        ReadAction.run<RuntimeException> {
            modules.forEach { index.filesFor(it) }
        }
        val warmElapsedMs = (System.nanoTime() - warmStart) / 1_000_000.0
        val warmSweeps = index.rebuildCount() - warmBefore

        report(legacy, coldSweeps, coldElapsedMs, warmSweeps, warmElapsedMs)

        // Identical answer. Without this the rest is meaningless.
        assertEquals("the index must return exactly what the old algorithm did", legacyResult, cachedResult)

        // Every module got its own two files, so the fixture really is exercising the work.
        assertEquals(MODULES, legacyResult.size)
        assertEquals(MATCHING_PER_MODULE, legacyResult.values.first().size)

        // The claim, stated as work rather than as time.
        assertEquals("old path sweeps once per module", MODULES, legacy.sweeps)
        assertEquals("indexed path sweeps once per tree build", 1, coldSweeps)
        assertEquals("a rebuild with nothing changed sweeps not at all", 0, warmSweeps)
        assertTrue("old path runs containment checks per file per module", legacy.containmentChecks > 0)
    }

    /**
     * The pre-refactor `getProjectFilesForModule`: sweep the whole project, then ask every module
     * whether it contains each matched file. Counting is the only addition.
     */
    private fun legacyFilesForModule(module: Module, counters: Counters): Set<String> {
        val groups = AndroidViewSettings.getInstance().projectFileGroups
        val pool = legacySweep(groups.flatMap { it.patterns }, counters)

        return pool
            .filter { (file, relativePath) ->
                AndroidViewNodeUtils.matchesAnyGroup(file.name, relativePath, groups)
            }
            .filter { (file, _) ->
                counters.containmentChecks++
                if (ModuleUtilCore.moduleContainsFile(module, file, true)) {
                    true
                } else {
                    counters.containmentChecks++
                    ModuleUtilCore.moduleContainsFile(module, file, false)
                }
            }
            .mapTo(mutableSetOf()) { (file, _) -> file.relative() }
    }

    /**
     * The pre-refactor module-root walk. The fixture uses no path-based patterns, so the separate
     * directory-prefix scan the old code also ran is not reproduced here — its absence only makes
     * the old numbers below look *better* than they were.
     */
    private fun legacySweep(patterns: List<String>, counters: Counters): List<Pair<VirtualFile, String>> {
        counters.sweeps++
        val base = baseDir()
        val inclusion = patterns.filter { !it.startsWith("!") }
        val result = mutableListOf<Pair<VirtualFile, String>>()

        for (module in ModuleManager.getInstance(project).modules) {
            if (module.isDisposed) continue
            for (root in ModuleRootManager.getInstance(module).contentRoots) {
                for (child in root.children) {
                    counters.filesVisited++
                    if (!child.isValid || child.isDirectory) continue
                    val relativePath = VfsUtil.getRelativePath(child, base, '/') ?: child.name
                    if (AndroidViewNodeUtils.matchesPatterns(child.name, relativePath, inclusion)) {
                        result.add(child to relativePath)
                    }
                }
            }
        }
        return result
    }

    private fun report(
        legacy: Counters,
        coldSweeps: Int,
        coldMs: Double,
        warmSweeps: Int,
        warmMs: Double
    ) {
        val line = "-".repeat(78)
        val out = StringBuilder()
        fun emit(text: String) { println(text); out.appendLine(text) }
        emit(line)
        emit("A2PT index benchmark — $MODULES modules x $FILES_PER_MODULE files")
        emit(line)
        emit(String.format("%-34s %14s %12s %14s", "", "sweeps", "files seen", "containsFile"))
        emit(String.format("%-34s %14d %12d %14d",
            "before (sweep per module)", legacy.sweeps, legacy.filesVisited, legacy.containmentChecks))
        emit(String.format("%-34s %14d %12d %14d",
            "after  (first tree build)", coldSweeps, legacy.filesVisited / legacy.sweeps, 0))
        emit(String.format("%-34s %14d %12d %14d",
            "after  (rebuild, unchanged)", warmSweeps, 0, 0))
        emit(line)
        emit(String.format("wall clock: before %.1f ms | after cold %.1f ms | after warm %.1f ms",
            legacy.elapsedMs, coldMs, warmMs))
        emit("(timings are indicative only and are never asserted)")
        emit(line)

        // The build echoes this after the test task, so the numbers survive a CI log.
        System.getProperty("a2pt.benchmark.report")?.let { path ->
            val file = File(path)
            file.parentFile?.mkdirs()
            file.writeText(out.toString())
        }
    }

    private class Counters {
        var sweeps = 0
        var filesVisited = 0
        var containmentChecks = 0
        var elapsedMs = 0.0
    }

    private fun VirtualFile.relative(): String =
        VfsUtil.getRelativePath(this, baseDir(), '/') ?: name

    private fun baseDir(): VirtualFile {
        val path = project.basePath ?: error("project has no base path")
        File(path).mkdirs()
        return LocalFileSystem.getInstance().refreshAndFindFileByPath(path)
            ?: error("project base dir not found: $path")
    }
}
