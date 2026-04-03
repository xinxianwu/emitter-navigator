package com.github.xinxianwu.emitternavigator.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager

class FindEmitterPairsAction : AnAction() {

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return

        val virtualFile = FileEditorManager.getInstance(project).selectedFiles.firstOrNull()
            ?: return Messages.showInfoMessage(project, "No file open", "Emitter Navigator")

        val psiFile = PsiManager.getInstance(project).findFile(virtualFile)
            ?: return Messages.showInfoMessage(project, "Cannot parse file", "Emitter Navigator")

        val emitMethods = setOf("emit", "trigger", "fire")
        val onMethods   = setOf("on", "once", "addListener")

        val emits = mutableListOf<Pair<String, Int>>()
        val ons   = mutableListOf<Pair<String, Int>>()

        fun walk(el: PsiElement) {
            val className = el::class.simpleName ?: ""

            if (className == "JSCallExpressionImpl") {
                val children = el.children

                val refExpr = children.firstOrNull {
                    it::class.simpleName == "JSReferenceExpressionImpl"
                }
                val methodName = refExpr?.children
                    ?.lastOrNull { it::class.simpleName == "LeafPsiElement" }
                    ?.text

                val argList = children.firstOrNull {
                    it::class.simpleName == "JSArgumentListImpl"
                }
                val firstLiteral = argList?.children?.firstOrNull {
                    it::class.simpleName == "JSLiteralExpressionImpl"
                }
                val eventName = firstLiteral?.text
                    ?.removeSurrounding("'")
                    ?.removeSurrounding("\"")

                if (methodName != null && eventName != null) {
                    val line = (psiFile.viewProvider.document
                        ?.getLineNumber(el.textOffset) ?: 0) + 1

                    when (methodName) {
                        in emitMethods -> emits.add(eventName to line)
                        in onMethods   -> ons.add(eventName to line)
                    }
                }
            }

            el.children.forEach { walk(it) }
        }

        walk(psiFile)

        val result = buildString {
            appendLine("emit: ${emits.size}, on: ${ons.size}")
            appendLine()
            val allNames = (emits.map { it.first } + ons.map { it.first }).toSet()
            if (allNames.isEmpty()) {
                appendLine("No emit/on found")
            } else {
                allNames.sorted().forEach { name ->
                    val e = emits.filter { it.first == name }.map { "L${it.second}" }
                    val o = ons.filter { it.first == name }.map { "L${it.second}" }
                    appendLine("'$name'  emit@[${e.joinToString()}]  on@[${o.joinToString()}]")
                }
            }
        }

        Messages.showInfoMessage(project, result, "Emitter Navigator")
    }
}