package com.github.xinxianwu.emitternavigator.navigation

import com.github.xinxianwu.emitternavigator.settings.EmitterNavigatorSettings
import com.github.xinxianwu.emitternavigator.settings.MethodConfig
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope

class EmitterGotoDeclarationHandler : GotoDeclarationHandler {

    private val settings get() = EmitterNavigatorSettings.instance

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

        val callExpr = argList.parent ?: return null
        if (callExpr::class.simpleName != "JSCallExpressionImpl") return null

        val methodName = callExpr.children
            .firstOrNull { it::class.simpleName == "JSReferenceExpressionImpl" }
            ?.children?.lastOrNull { it::class.simpleName == "LeafPsiElement" }
            ?.text ?: return null

        val emitConfigs = settings.emitMethodConfigs
        val onConfigs   = settings.onMethodConfigs

        val emitConfig = emitConfigs[methodName]
        val onConfig   = onConfigs[methodName]
        val config = emitConfig ?: onConfig ?: return null
        val isEmit = emitConfig != null

        // 確認 literal 是在正確的 arg index 位置
        val arguments = getArguments(argList)
        if (arguments.getOrNull(config.eventArgIndex) != literal) return null

        val eventName = literal.text.removeSurrounding("'").removeSurrounding("\"")
        val pairedConfigs = if (isEmit) onConfigs else emitConfigs

        val project = sourceElement.project
        val scope = GlobalSearchScope.projectScope(project)
        val psiManager = PsiManager.getInstance(project)
        val targets = mutableListOf<PsiElement>()

        for (ext in listOf("js", "ts", "jsx", "tsx", "vue")) {
            for (vFile in FilenameIndex.getAllFilesByExt(project, ext, scope)) {
                val psiFile = psiManager.findFile(vFile) ?: continue
                collectPairedCalls(psiFile, eventName, pairedConfigs) { callElement, pairedMethod ->
                    targets.add(EmitterPsiElementWrapper(callElement, eventName, pairedMethod))
                }
            }
        }

        // 排序：相同檔案的放在最前面，然後按行號排序
        val currentFile = sourceElement.containingFile
        val sorted = targets.sortedWith(compareBy<PsiElement> { element ->
            if (element.containingFile == currentFile) 0 else 1
        }.thenBy { element ->
            val virtualFile = element.containingFile?.virtualFile
            val document = virtualFile?.let { FileDocumentManager.getInstance().getDocument(it) }
            document?.getLineNumber(element.textOffset) ?: Int.MAX_VALUE
        })

        return if (sorted.isEmpty()) null else sorted.toTypedArray()
    }

    // 取得 argument list 中的實際參數（過濾掉逗號、括號、空白等非表達式節點）
    private fun getArguments(argList: PsiElement): List<PsiElement> {
        return argList.children.filter { child ->
            val name = child::class.simpleName ?: ""
            name != "LeafPsiElement" && name != "PsiWhiteSpaceImpl"
        }
    }

    private fun collectPairedCalls(
        root: PsiElement,
        eventName: String,
        pairedConfigs: Map<String, MethodConfig>,
        consumer: (PsiElement, String) -> Unit
    ) {
        if (root::class.simpleName == "JSCallExpressionImpl") {
            val name = root.children
                .firstOrNull { it::class.simpleName == "JSReferenceExpressionImpl" }
                ?.children?.lastOrNull { it::class.simpleName == "LeafPsiElement" }
                ?.text
            val config = name?.let { pairedConfigs[it] }
            if (config != null) {
                val args = root.children.firstOrNull { it::class.simpleName == "JSArgumentListImpl" }
                val eventArg = args?.let { getArguments(it).getOrNull(config.eventArgIndex) }
                if (eventArg != null && eventArg::class.simpleName == "JSLiteralExpressionImpl") {
                    val ev = eventArg.text.removeSurrounding("'").removeSurrounding("\"")
                    if (ev == eventName) {
                        consumer(root, name)
                    }
                }
            }
        }
        root.children.forEach { collectPairedCalls(it, eventName, pairedConfigs, consumer) }
    }
}