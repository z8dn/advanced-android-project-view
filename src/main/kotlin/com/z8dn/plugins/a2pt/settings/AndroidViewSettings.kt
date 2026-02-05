package com.z8dn.plugins.a2pt.settings

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
)

@State(
    name = "AndroidViewSettings",
    storages = [Storage("androidViewSettings.xml", roamingType = RoamingType.DEFAULT)]
)
class AndroidViewSettings : PersistentStateComponent<AndroidViewSettings> {

    var showBuildDirectory = false
    var projectFileGroups: MutableList<ProjectFileGroup> = mutableListOf()
    var showProjectFilesInModule = false

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
