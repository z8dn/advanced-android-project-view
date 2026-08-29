package com.z8dn.plugins.a2pt.index

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.module.Module
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
 * Pins the file discovery and per-module attribution the project tree is built from, against a
 * real multi-module fixture.
 *
 * These assertions exist to guard a performance refactor: the scan is moving behind a cached
 * index, and `byModule` will be built during the content-root walk rather than by filtering the
 * whole pool with [com.intellij.openapi.module.ModuleUtilCore.moduleContainsFile] afterwards.
 * That is the one change capable of silently altering which files appear under which node, so
 * every expectation here must survive it **unchanged**.
 *
 * Unlike the other tests in this source set, this one needs the platform, so it extends
 * [HeavyPlatformTestCase] (JUnit 3): methods are named `testXxx` rather than carrying `@Test`.
 */
class ProjectFileIndexBehaviorTest : HeavyPlatformTestCase() {

    private lateinit var appModule: Module
    private lateinit var coreModule: Module
    private lateinit var nestedModule: Module

    private var savedGroups: MutableList<ProjectFileGroup> = mutableListOf()
    private var savedShowInModule = false

    /**
     * ```
     * <base>/app/build.gradle.kts        content root of :app
     * <base>/app/README.md
     * <base>/core/build.gradle.kts       content root of :core
     * <base>/core/NOTES.md
     * <base>/core/nested/README.md       content root of :nested, inside :core's root
     * <base>/docs/guide.md               outside every content root
     * <base>/docs/archive/old.md
     * ```
     */
    override fun setUp() {
        super.setUp()

        val settings = AndroidViewSettings.getInstance()
        savedGroups = settings.projectFileGroups
        savedShowInModule = settings.showProjectFilesInModule
        settings.projectFileGroups = mutableListOf()

        val base = baseDir()
        VfsTestUtil.createFile(base, "app/build.gradle.kts", "")
        VfsTestUtil.createFile(base, "app/README.md", "")
        VfsTestUtil.createFile(base, "core/build.gradle.kts", "")
        VfsTestUtil.createFile(base, "core/NOTES.md", "")
        VfsTestUtil.createFile(base, "core/nested/README.md", "")
        VfsTestUtil.createFile(base, "docs/guide.md", "")
        VfsTestUtil.createFile(base, "docs/archive/old.md", "")

        appModule = moduleWithContentRoot("app", "app")
        coreModule = moduleWithContentRoot("core", "core")
        nestedModule = moduleWithContentRoot("nested", "core/nested")
    }

    override fun tearDown() {
        try {
            val settings = AndroidViewSettings.getInstance()
            settings.projectFileGroups = savedGroups
            settings.showProjectFilesInModule = savedShowInModule
        } finally {
            super.tearDown()
        }
    }

    // region discovery

    /**
     * A filename-only pattern reaches the immediate children of each content root and nothing
     * deeper. This is why `*.md` does not find `docs/guide.md`, and why recursive matching is a
     * separate change rather than a side effect of this one.
     */
    fun testFilenamePatternFindsOnlyContentRootChildren() {
        assertEquals(
            setOf("app/README.md", "core/NOTES.md", "core/nested/README.md"),
            collect("*.md")
        )
    }

    /** A path pattern whose first segment is literal scans that directory recursively. */
    fun testPathPatternScansItsDirectoryRecursively() {
        assertEquals(setOf("docs/guide.md", "docs/archive/old.md"), collect("docs/**"))
    }

    /**
     * Discovery deliberately drops `!` patterns: the candidate pool is shared across groups, so
     * one group's exclusion must not hide a file another group includes. Consumers reapply them.
     */
    fun testDiscoveryIgnoresExclusions() {
        val found = collect("*.md", "!README.md")

        assertTrue("exclusions must not be applied during discovery", found.contains("app/README.md"))
        assertTrue(found.contains("core/nested/README.md"))
    }

    // endregion

    // region per-module attribution

    /** The assertion this refactor most needs to preserve. */
    fun testEachFileIsAttributedToItsOwnModule() {
        setGroups(group("Docs", "*.md"))

        assertEquals(setOf("app/README.md"), filesFor(appModule))
        assertEquals(setOf("core/NOTES.md"), filesFor(coreModule))
        assertEquals(setOf("core/nested/README.md"), filesFor(nestedModule))
    }

    /**
     * A nested content root belongs to the inner module only. Pinned because building `byModule`
     * during the walk must reproduce this split rather than hand the file to both modules.
     */
    fun testNestedContentRootFileDoesNotLeakIntoTheOuterModule() {
        setGroups(group("Docs", "*.md"))

        assertFalse(filesFor(coreModule).contains("core/nested/README.md"))
    }

    /** Group exclusions are reapplied per module, unlike during discovery. */
    fun testModuleAttributionReappliesGroupExclusions() {
        setGroups(group("Docs", "*.md", "!NOTES.md"))

        assertEquals(setOf("app/README.md"), filesFor(appModule))
        assertTrue("the excluded file must not reach its module", filesFor(coreModule).isEmpty())
    }

    /** Files outside every content root belong to no module, so module display mode drops them. */
    fun testFileOutsideEveryContentRootIsAttributedToNoModule() {
        setGroups(group("Docs", "docs/**"))

        assertTrue(filesFor(appModule).isEmpty())
        assertTrue(filesFor(coreModule).isEmpty())
        assertTrue(filesFor(nestedModule).isEmpty())
    }

    /** With no groups configured there is nothing to discover and nothing to attribute. */
    fun testNoGroupsYieldsNothing() {
        setGroups()

        assertTrue(filesFor(appModule).isEmpty())
    }

    // endregion

    // region cache invalidation

    /**
     * Editing a group must invalidate the cached sweep even when the old and new patterns share a
     * hash. `"Aa"` and `"BB"` both hash to 2112, so a stamp-based cache key would keep serving the
     * pool discovered for the old patterns until something unrelated invalidated it.
     */
    fun testGroupEditWithACollidingHashStillRebuilds() {
        val index = ProjectFileGroupIndex.getInstance(project)

        setGroups(group("Docs", "Aa"))
        ReadAction.run<RuntimeException> { index.pool() }
        val rebuildsAfterFirstRead = index.rebuildCount()

        setGroups(group("Docs", "BB"))
        ReadAction.run<RuntimeException> { index.pool() }

        assertTrue(
            "a group edit whose patterns collide by hash must still rebuild the index",
            index.rebuildCount() > rebuildsAfterFirstRead
        )
    }

    /** Reading twice with nothing changed must not sweep twice — the point of the cache. */
    fun testRepeatedReadWithNoChangeDoesNotRebuild() {
        val index = ProjectFileGroupIndex.getInstance(project)

        setGroups(group("Docs", "*.md"))
        ReadAction.run<RuntimeException> { index.pool() }
        val rebuildsAfterFirstRead = index.rebuildCount()

        ReadAction.run<RuntimeException> { index.pool() }

        assertEquals(
            "an unchanged read must be served from the snapshot",
            rebuildsAfterFirstRead,
            index.rebuildCount()
        )
    }

    // endregion

    /**
     * The project's base directory, created on disk first.
     *
     * HeavyPlatformTestCase sets `basePath` to a temp path but does not materialise the directory,
     * so the VFS cannot resolve it until it exists. Everything in the fixture has to live under
     * this directory, because the code under test derives every relative path from it.
     */
    private fun baseDir(): VirtualFile {
        val path = project.basePath ?: error("project has no base path")
        File(path).mkdirs()
        return LocalFileSystem.getInstance().refreshAndFindFileByPath(path)
            ?: error("project base dir not found: $path")
    }

    private fun moduleWithContentRoot(name: String, relativePath: String): Module {
        val module = createModule(name)
        val root = baseDir().findFileByRelativePath(relativePath)
            ?: error("content root not created: $relativePath")
        PsiTestUtil.addContentRoot(module, root)
        return module
    }

    private fun setGroups(vararg groups: ProjectFileGroup) {
        AndroidViewSettings.getInstance().projectFileGroups = groups.toMutableList()
    }

    private fun group(name: String, vararg patterns: String) =
        ProjectFileGroup(name, patterns.toMutableList())

    /** Relative paths of everything [AndroidViewNodeUtils.collectMatchingFiles] discovers. */
    private fun collect(vararg patterns: String): Set<String> =
        ReadAction.compute<Set<String>, RuntimeException> {
            AndroidViewNodeUtils.collectMatchingFiles(project, patterns.toList())
                .mapTo(mutableSetOf()) { (_, relativePath) -> relativePath }
        }

    /** Relative paths of the files the tree would show inside [module]. */
    private fun filesFor(module: Module): Set<String> =
        ReadAction.compute<Set<String>, RuntimeException> {
            val base = baseDir()
            AndroidViewNodeUtils.getProjectFilesForModule(module)
                .mapTo(mutableSetOf()) { file -> VfsUtil.getRelativePath(file, base, '/') ?: file.name }
        }
}
