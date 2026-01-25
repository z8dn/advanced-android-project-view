package com.z8dn.plugins.a2pt

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.RoamingType
import com.intellij.util.xmlb.XmlSerializerUtil

@State(
    name = "AndroidViewSettings",
    storages = [Storage("androidViewSettings.xml", roamingType = RoamingType.LOCAL)]
)
class AndroidViewSettings : PersistentStateComponent<AndroidViewSettings> {

    var showBuildDirectory = false
    var showCustomFiles = false
    var filePatterns: MutableList<String> = mutableListOf()
    var groupCustomNodes = true // If true, show custom nodes in top-level grouping; if false, show in modules
    var customGroupings: MutableList<CustomNodeGrouping> = mutableListOf()

    companion object {
        @JvmStatic
        fun getInstance(): AndroidViewSettings {
            return ApplicationManager.getApplication()
                .getService(AndroidViewSettings::class.java)
        }
    }

    override fun getState(): AndroidViewSettings {
        // Return a copy without the legacy field to avoid persisting it
        val state = AndroidViewSettings()
        state.showBuildDirectory = showBuildDirectory
        state.showCustomFiles = showCustomFiles
        state.filePatterns = filePatterns.toMutableList()
        state.groupCustomNodes = groupCustomNodes
        state.customGroupings = customGroupings.map { it.copy() }.toMutableList()
        return state
    }

    override fun loadState(state: AndroidViewSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }
}
