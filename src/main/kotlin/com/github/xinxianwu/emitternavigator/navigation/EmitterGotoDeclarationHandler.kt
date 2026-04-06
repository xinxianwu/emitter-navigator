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

private const val MAX_ARG_SCAN = 9

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

        // 確認游標所在的 literal 符合此方法的 event arg 規則
        val arguments = getArguments(argList)
        val literalIndex = arguments.indexOf(literal)
        if (literalIndex !in 0 until MAX_ARG_SCAN) return null
        if (config.eventArgIndex != null && config.eventArgIndex != literalIndex) return null

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
                val arguments = args?.let { getArguments(it) } ?: emptyList()

                val matchedLiteral = if (config.eventArgIndex != null) {
                    // 指定位置：只看該 index
                    arguments.getOrNull(config.eventArgIndex)?.takeIf { arg ->
                        arg::class.simpleName == "JSLiteralExpressionImpl" &&
                        arg.text.removeSurrounding("'").removeSurrounding("\"") == eventName
                    }
                } else {
                    // 任意位置：掃描前 N 個參數中第一個匹配的字串 literal
                    arguments.take(MAX_ARG_SCAN).firstOrNull { arg ->
                        arg::class.simpleName == "JSLiteralExpressionImpl" &&
                        arg.text.removeSurrounding("'").removeSurrounding("\"") == eventName
                    }
                }

                // 導航目標指向字串 literal，游標精準落在 event 字符上
                if (matchedLiteral != null) consumer(matchedLiteral, name)
            }
        }
        root.children.forEach { collectPairedCalls(it, eventName, pairedConfigs, consumer) }
    }
}