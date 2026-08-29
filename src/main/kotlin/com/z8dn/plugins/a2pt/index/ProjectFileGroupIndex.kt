package com.z8dn.plugins.a2pt.index

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.z8dn.plugins.a2pt.settings.AndroidViewSettings
import com.z8dn.plugins.a2pt.settings.ProjectFileGroup
import com.z8dn.plugins.a2pt.utils.AndroidViewNodeUtils
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * The single place the project tree asks what files the configured groups claim, and the cache
 * that keeps it from asking the VFS more than once per tree build.
 *
 * Before this existed, each of the three tree providers swept the VFS itself, and
 * [AndroidViewNodeUtils.getProjectFilesForModule] swept the whole project once **per module** —
 * so a tree build cost N sweeps on an N-module project, repeated on every structure rebuild.
 *
 * Project-level rather than application-level, even though [AndroidViewSettings] is an
 * application service: the VFS and the module graph are per-project, so anything derived from
 * them must be too.
 *
 * Read methods must be called inside a read action — they touch the VFS.
 */
@Service(Service.Level.PROJECT)
class ProjectFileGroupIndex(private val project: Project) {

    /**
     * An immutable view of one sweep.
     *
     * Holds only [VirtualFile] and [String] — never PSI and never tree nodes. Those invalidate
     * independently of this cache, and a stale `PsiFile` in a long-lived snapshot is a
     * `PsiInvalidElementAccessException` waiting to happen.
     */
    private class Snapshot(
        val pool: List<Pair<VirtualFile, String>>,
        val byModule: Map<Module, List<VirtualFile>>,
        /** The group definitions this was swept for, compared structurally on every read. */
        val groups: List<ProjectFileGroup>
    )

    private val snapshot = AtomicReference<Snapshot?>(null)
    private val rebuildLock = Any()

    /** How many sweeps this index has actually performed. The only observable proof it caches. */
    private val rebuilds = AtomicInteger()

    /** Every file some group claims, paired with its path relative to the project root. */
    fun pool(): List<Pair<VirtualFile, String>> = current().pool.filterValid()

    /**
     * The files [group] claims, with its own exclusions applied, each paired with its path
     * relative to the project root — the node needs that path to qualify a file that has no
     * parent directory name to show.
     *
     * The candidate pool deliberately ignores `!` patterns, because discovery is shared across
     * groups, so this reapplies them.
     *
     * Filtered per call rather than precomputed into the snapshot: matching a cached list against
     * already-compiled globs is nothing next to a VFS sweep, and keying a map by group name would
     * quietly couple this cache to the settings dialog's duplicate-name validation.
     */
    fun filesFor(group: ProjectFileGroup): List<Pair<VirtualFile, String>> =
        pool().filter { (file, relativePath) ->
            AndroidViewNodeUtils.matchesPatterns(file.name, relativePath, group.patterns)
        }

    /** The files to show inside [module] when `showProjectFilesInModule` is enabled. */
    fun filesFor(module: Module): List<VirtualFile> =
        (current().byModule[module] ?: emptyList()).filter { it.isValid }

    /** Drops the cached sweep. The next read rebuilds it. */
    fun invalidate() {
        snapshot.set(null)
    }

    /**
     * The current snapshot, rebuilding if it is missing or was built against different groups.
     *
     * Rebuilds under a lock rather than racing with `compareAndSet`: tree nodes compute their
     * children off the EDT, so on the first read after an invalidation every module node arrives
     * at once, and letting them all sweep concurrently would reintroduce exactly the cost this
     * class exists to remove.
     */
    private fun current(): Snapshot {
        val groups = currentGroupDefinitions()
        snapshot.get()?.let { if (it.groups == groups) return it }

        synchronized(rebuildLock) {
            // another thread may have rebuilt while this one waited on the lock
            snapshot.get()?.let { if (it.groups == groups) return it }

            // A cancelled read action must not leave a partial sweep cached, so this deliberately
            // does not catch ProcessCanceledException: it propagates and `snapshot` stays null.
            return build(groups).also { snapshot.set(it) }
        }
    }

    /** Sweeps performed since this index was created. */
    @TestOnly
    fun rebuildCount(): Int = rebuilds.get()

    /** Sweeps for exactly the [groups] the caller compared against, never a fresh read of them. */
    private fun build(groups: List<ProjectFileGroup>): Snapshot {
        val startedAt = System.nanoTime()
        rebuilds.incrementAndGet()
        val swept = AndroidViewNodeUtils.sweep(project, groups)
        val pool = swept.pool
        val byModule = swept.byModule

        if (LOG.isDebugEnabled) {
            val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
            LOG.debug(
                "rebuilt index for ${project.name}: ${pool.size} candidate file(s), " +
                    "${groups.size} group(s), ${byModule.size} module(s) in ${elapsedMs}ms"
            )
        }
        return Snapshot(pool, byModule, groups)
    }

    /**
     * A deep copy of the configured groups, compared structurally rather than by hash.
     *
     * [AndroidViewSettings.projectFileGroups] is a `MutableList` of objects holding their own
     * `MutableList` of patterns, and every caller mutates it in place — the settings dialog
     * clears and refills it, and the project-view popup actions edit the lists directly. A
     * modification counter would have to be bumped at each of those sites and would rot the first
     * time someone added another, so the cache compares definitions instead.
     *
     * Those definitions are compared with `==`, not a hash: `"Aa"` and `"BB"` share a
     * `String.hashCode()`, so an Int stamp would silently serve a pool discovered for the old
     * patterns. Copying is what makes the comparison meaningful — holding the live objects would
     * compare a list against itself after it was mutated in place.
     */
    private fun currentGroupDefinitions(): List<ProjectFileGroup> =
        AndroidViewSettings.getInstance().projectFileGroups
            .map { ProjectFileGroup(it.groupName, it.patterns.toMutableList()) }

    /**
     * A snapshot can briefly outlive a deletion — the VFS event fires before the listener runs —
     * so reads drop invalid files rather than handing them to the tree.
     */
    private fun List<Pair<VirtualFile, String>>.filterValid(): List<Pair<VirtualFile, String>> =
        filter { (file, _) -> file.isValid }

    companion object {
        private val LOG = Logger.getInstance(ProjectFileGroupIndex::class.java)

        @JvmStatic
        fun getInstance(project: Project): ProjectFileGroupIndex = project.service()

        /** Drops the cached sweep for every open project any of [paths] could affect. */
        private fun invalidateProjectsContaining(paths: List<String>) {
            for (project in ProjectManager.getInstance().openProjects) {
                if (project.isDisposed) continue
                val basePath = project.basePath
                if (basePath == null || paths.any { it.startsWith(basePath) }) {
                    getInstance(project).invalidate()
                }
            }
        }
    }

    /**
     * Drops the cached sweep when the set of files on disk changes.
     *
     * `prepareChange` runs off the EDT without a read action, so the filter here is cheap — which
     * matters, because it sees every VFS event in the IDE. Two exclusions carry it:
     *
     * - **content changes are ignored entirely.** Editing a file cannot change which group claims
     *   it, and content events are the overwhelming majority of what the VFS emits.
     * - **the directories the sweep already skips are ignored.** A Gradle build writing thousands
     *   of files into `build/` must not invalidate an index that never looked in `build/`. This is
     *   why the listener is hand-written rather than a `CachedValuesManager` dependency on
     *   `VFS_STRUCTURE_MODIFICATIONS`, which bumps on any structural change anywhere and would
     *   thrash precisely during the workload this plugin exists to make visible.
     */
    internal class VfsInvalidator : AsyncFileListener {
        override fun prepareChange(events: List<VFileEvent>): AsyncFileListener.ChangeApplier? {
            val relevant = events.filter { it.isRelevant() }
            if (relevant.isEmpty()) return null

            val paths = relevant.map { it.path }
            return object : AsyncFileListener.ChangeApplier {
                override fun afterVfsChange() {
                    invalidateProjectsContaining(paths)
                }
            }
        }

        private fun VFileEvent.isRelevant(): Boolean =
            this !is VFileContentChangeEvent && !AndroidViewNodeUtils.isInIgnoredDirectory(path)
    }

    /**
     * Drops the cached sweep when a Gradle sync rewrites the module graph.
     *
     * Content roots define the sweep's surface, so without this the index goes stale after every
     * sync — the worst failure mode available, because to a user it presents as the tree randomly
     * being wrong.
     */
    internal class RootChangeInvalidator(private val project: Project) : ModuleRootListener {
        override fun rootsChanged(event: ModuleRootEvent) {
            if (!project.isDisposed) getInstance(project).invalidate()
        }
    }
}
