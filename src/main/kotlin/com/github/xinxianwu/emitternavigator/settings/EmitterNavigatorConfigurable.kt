package com.github.xinxianwu.emitternavigator.settings

import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel

class EmitterNavigatorConfigurable : Configurable {

    private var emitMethodsArea: JBTextArea? = null
    private var onMethodsArea: JBTextArea? = null

    override fun getDisplayName(): String = "Emitter Navigation"

    override fun createComponent(): JComponent {
        val emitArea = JBTextArea(6, 30).also { it.lineWrap = false }
        val onArea = JBTextArea(6, 30).also { it.lineWrap = false }
        emitMethodsArea = emitArea
        onMethodsArea = onArea

        reset()

        return FormBuilder.createFormBuilder()
            .addLabeledComponent(
                JBLabel("<html>Emit methods (one per line):<br><small>Format: <code>methodName</code> or <code>methodName:argIndex</code> (0-based)<br>e.g. <code>emit</code>, <code>send_io_room:1</code></small></html>"),
                JBScrollPane(emitArea)
            )
            .addVerticalGap(8)
            .addLabeledComponent(
                JBLabel("<html>On methods (one per line):<br><small>Format: <code>methodName</code> or <code>methodName:argIndex</code> (0-based)<br>e.g. <code>on</code>, <code>listen_room:1</code></small></html>"),
                JBScrollPane(onArea)
            )
            .addComponentFillVertically(JPanel(), 0)
            .panel
    }

    override fun isModified(): Boolean {
        val settings = EmitterNavigatorSettings.instance
        return emitMethodsArea?.text != settings.emitMethods ||
               onMethodsArea?.text != settings.onMethods
    }

    override fun apply() {
        val settings = EmitterNavigatorSettings.instance
        emitMethodsArea?.text?.let { settings.emitMethods = it }
        onMethodsArea?.text?.let { settings.onMethods = it }
    }

    override fun reset() {
        val settings = EmitterNavigatorSettings.instance
        emitMethodsArea?.text = settings.emitMethods
        onMethodsArea?.text = settings.onMethods
    }

    override fun disposeUIResources() {
        emitMethodsArea = null
        onMethodsArea = null
    }
}