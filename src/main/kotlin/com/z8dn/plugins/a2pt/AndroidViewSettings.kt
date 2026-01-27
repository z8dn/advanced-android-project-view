package com.z8dn.plugins.a2pt

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.RoamingType
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * Data class representing a project file group with its name and patterns.
 */
data class ProjectFileGroup(
    var groupName: String = "",
    var patterns: MutableList<String> = mutableListOf()
) {
    // No-arg constructor for XML serialization
    constructor() : this("", mutableListOf())
}

@State(
    name = "AndroidViewSettings",
    storages = [Storage("androidViewSettings.xml", roamingType = RoamingType.LOCAL)]
)
class AndroidViewSettings : PersistentStateComponent<AndroidViewSettings> {

    var showBuildDirectory = false
    var projectFileGroups: MutableList<ProjectFileGroup> = mutableListOf(
        ProjectFileGroup("Documentation", mutableListOf("README.md"))
    )
    var showProjectFilesInModule = false // If true, show project files under each module; if false, show in project-level group

    companion object {
        @JvmStatic
        fun getInstance(): AndroidViewSettings {
            return ApplicationManager.getApplication()
                .getService(AndroidViewSettings::class.java)
        }
    }

    override fun getState(): AndroidViewSettings = this

    override fun loadState(state: AndroidViewSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }
}
