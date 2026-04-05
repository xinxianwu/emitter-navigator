package com.github.xinxianwu.emitternavigator.navigation

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement

class EmitterGotoDeclarationHandler : GotoDeclarationHandler {

    private val emitMethods = setOf("emit", "trigger", "fire")
    private val onMethods   = setOf("on", "once", "addListener")

    override fun getGotoDeclarationTargets(
        sourceElement: PsiElement?,
        offset: Int,
        editor: Editor
    ): Array<PsiElement>? {
        sourceElement ?: return null

        // sourceElement is the leaf token; its parent should be the string literal
        val literal = sourceElement.parent ?: return null
        if (literal::class.simpleName != "JSLiteralExpressionImpl") return null

        // literal must be the first JS expression argument inside a call expression
        val argList = literal.parent ?: return null
        if (argList::class.simpleName != "JSArgumentListImpl") return null
        val firstLiteral = argList.children.firstOrNull { it::class.simpleName == "JSLiteralExpressionImpl" }
        if (firstLiteral != literal) return null

        val callExpr = argList.parent ?: return null
        if (callExpr::class.simpleName != "JSCallExpressionImpl") return null

        val methodName = callExpr.children
            .firstOrNull { it::class.simpleName == "JSReferenceExpressionImpl" }
            ?.children?.lastOrNull { it::class.simpleName == "LeafPsiElement" }
            ?.text ?: return null

        val isEmit = methodName in emitMethods
        val isOn   = methodName in onMethods
        if (!isEmit && !isOn) return null

        val eventName = literal.text.removeSurrounding("'").removeSurrounding("\"")
        val pairedMethods = if (isEmit) onMethods else emitMethods

        val targets = mutableListOf<PsiElement>()

        fun walk(el: PsiElement) {
            if (el::class.simpleName == "JSCallExpressionImpl") {
                val name = el.children
                    .firstOrNull { it::class.simpleName == "JSReferenceExpressionImpl" }
                    ?.children?.lastOrNull { it::class.simpleName == "LeafPsiElement" }
                    ?.text

                if (name in pairedMethods) {
                    val args = el.children.firstOrNull { it::class.simpleName == "JSArgumentListImpl" }
                    val lit  = args?.children?.firstOrNull { it::class.simpleName == "JSLiteralExpressionImpl" }
                    val ev   = lit?.text?.removeSurrounding("'")?.removeSurrounding("\"")

                    if (ev == eventName && lit != null) {
                        targets.add(lit)
                    }
                }
            }
            el.children.forEach { walk(it) }
        }

        walk(sourceElement.containingFile ?: return null)

        return if (targets.isEmpty()) null else targets.toTypedArray()
    }
}