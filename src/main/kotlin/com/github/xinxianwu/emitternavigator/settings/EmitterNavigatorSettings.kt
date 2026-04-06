package com.github.xinxianwu.emitternavigator.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

data class MethodConfig(val name: String, val eventArgIndex: Int = 0)

@State(
    name = "EmitterNavigatorSettings",
    storages = [Storage("emitterNavigator.xml")]
)
class EmitterNavigatorSettings : PersistentStateComponent<EmitterNavigatorSettings.State> {

    class State {
        var emitMethods: String = "emit\n"
        var onMethods: String = "on\n"
    }

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    var emitMethods: String
        get() = myState.emitMethods
        set(value) { myState.emitMethods = value }

    var onMethods: String
        get() = myState.onMethods
        set(value) { myState.onMethods = value }

    val emitMethodConfigs: Map<String, MethodConfig>
        get() = parseMethodConfigs(emitMethods)

    val onMethodConfigs: Map<String, MethodConfig>
        get() = parseMethodConfigs(onMethods)

    companion object {
        val instance: EmitterNavigatorSettings
            get() = ApplicationManager.getApplication().getService(EmitterNavigatorSettings::class.java)

        fun parseMethodConfigs(text: String): Map<String, MethodConfig> {
            return text.lines()
                .mapNotNull { line ->
                    val trimmed = line.trim()
                    if (trimmed.isEmpty()) return@mapNotNull null
                    val parts = trimmed.split(":")
                    val name = parts[0].trim()
                    val index = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 0
                    if (name.isNotEmpty()) name to MethodConfig(name, index) else null
                }
                .toMap()
        }
    }
}