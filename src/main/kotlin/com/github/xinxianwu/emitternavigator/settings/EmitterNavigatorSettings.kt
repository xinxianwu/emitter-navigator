package com.github.xinxianwu.emitternavigator.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

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

    val emitMethodSet: Set<String>
        get() = emitMethods.lines().map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    val onMethodSet: Set<String>
        get() = onMethods.lines().map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    companion object {
        val instance: EmitterNavigatorSettings
            get() = ApplicationManager.getApplication().getService(EmitterNavigatorSettings::class.java)
    }
}