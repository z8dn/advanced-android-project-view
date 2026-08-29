package com.z8dn.plugins.a2pt.index

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.z8dn.plugins.a2pt.settings.AndroidViewSettings
import com.z8dn.plugins.a2pt.settings.ProjectFileGroup
import com.z8dn.plugins.a2pt.utils.AndroidViewNodeUtils

/**
 * The single place the project tree asks what files the configured groups claim.
 *
 * Before this existed, each of the three tree providers swept the VFS itself, and
 * [AndroidViewNodeUtils.getProjectFilesForModule] swept the whole project once **per module**.
 * Routing every consumer through one object is what makes caching that sweep a change to one
 * file rather than to the whole provider layer.
 *
 * Project-level rather than application-level, even though [AndroidViewSettings] is an
 * application service: the VFS and the module graph are per-project, so anything derived from
 * them must be too.
 *
 * Must be called inside a read action — this touches the VFS.
 */
@Service(Service.Level.PROJECT)
class ProjectFileGroupIndex(private val project: Project) {

    /** Every file some group claims, paired with its path relative to the project root. */
    fun pool(): List<Pair<VirtualFile, String>> =
        AndroidViewNodeUtils.getAllProjectFilesInProject(project)

    /**
     * The files [group] claims, with its own exclusions applied, each paired with its path
     * relative to the project root — the node needs that path to qualify a file that has no
     * parent directory name to show.
     *
     * [pool] deliberately ignores `!` patterns because discovery is shared across groups, so the
     * reapplication has to happen here.
     */
    fun filesFor(group: ProjectFileGroup): List<Pair<VirtualFile, String>> =
        filesFor(group, pool())

    /** [filesFor] against an already-computed pool, for callers building several groups at once. */
    fun filesFor(
        group: ProjectFileGroup,
        pool: List<Pair<VirtualFile, String>>
    ): List<Pair<VirtualFile, String>> =
        pool.filter { (file, relativePath) ->
            AndroidViewNodeUtils.matchesPatterns(file.name, relativePath, group.patterns)
        }

    /** The files to show inside [module] when `showProjectFilesInModule` is enabled. */
    fun filesFor(module: Module): List<VirtualFile> =
        AndroidViewNodeUtils.getProjectFilesForModule(module)

    companion object {
        @JvmStatic
        fun getInstance(project: Project): ProjectFileGroupIndex = project.service()
    }
}
