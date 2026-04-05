package com.github.xinxianwu.emitternavigator.navigation

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope

class EmitterGotoDeclarationHandler : GotoDeclarationHandler {

    private val emitMethods = setOf("emit", "trigger", "fire")
    private val onMethods   = setOf("on", "once", "addListener")

    override fun getGotoDeclarationTargets(
        sourceElement: PsiElement?,
        offset: Int,
        editor: Editor
    ): Array<PsiElement>? {
        sourceElement ?: return null

        val literal = sourceElement.parent ?: return null
        if (literal::class.simpleName != "JSLiteralExpressionImpl") return null

        val argList = literal.parent ?: return null
        if (argList::class.simpleName != "JSArgumentListImpl") return null
        if (argList.children.firstOrNull { it::class.simpleName == "JSLiteralExpressionImpl" } != literal) return null

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

        val project = sourceElement.project
        val scope = GlobalSearchScope.projectScope(project)
        val psiManager = PsiManager.getInstance(project)
        val targets = mutableListOf<PsiElement>()

        for (ext in listOf("js", "ts", "jsx", "tsx", "vue")) {
            for (vFile in FilenameIndex.getAllFilesByExt(project, ext, scope)) {
                val psiFile = psiManager.findFile(vFile) ?: continue
                collectPairedLiterals(psiFile, eventName, pairedMethods) { element, methodName ->
                    targets.add(EmitterPsiElementWrapper(element, eventName, methodName))
                }
            }
        }

        // 排序：相同檔案的放在最前面，然後按行號排序
        val currentFile = sourceElement.containingFile
        val sorted = targets.sortedWith(compareBy<PsiElement> { element ->
            // 先按是否為當前檔案排序（當前檔案 = 0，其他檔案 = 1）
            if (element.containingFile == currentFile) 0 else 1
        }.thenBy { element ->
            // 再按行號排序
            val virtualFile = element.containingFile?.virtualFile
            val document = virtualFile?.let { FileDocumentManager.getInstance().getDocument(it) }
            document?.getLineNumber(element.textOffset) ?: Int.MAX_VALUE
        })

        return if (sorted.isEmpty()) null else sorted.toTypedArray()
    }

    private fun collectPairedLiterals(
        root: PsiElement,
        eventName: String,
        pairedMethods: Set<String>,
        consumer: (PsiElement, String) -> Unit
    ) {
        if (root::class.simpleName == "JSCallExpressionImpl") {
            val name = root.children
                .firstOrNull { it::class.simpleName == "JSReferenceExpressionImpl" }
                ?.children?.lastOrNull { it::class.simpleName == "LeafPsiElement" }
                ?.text
            if (name != null && name in pairedMethods) {
                val args = root.children.firstOrNull { it::class.simpleName == "JSArgumentListImpl" }
                val lit  = args?.children?.firstOrNull { it::class.simpleName == "JSLiteralExpressionImpl" }
                val ev   = lit?.text?.removeSurrounding("'")?.removeSurrounding("\"")
                if (ev == eventName && lit != null) {
                    consumer(lit, name)
                }
            }
        }
        root.children.forEach { collectPairedLiterals(it, eventName, pairedMethods, consumer) }
    }
}