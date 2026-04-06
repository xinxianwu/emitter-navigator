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

        // 支援直接字串 literal 或變數 reference（const key = "event"）
        val expr = sourceElement.parent ?: return null
        val exprClass = expr::class.simpleName
        if (exprClass != "JSLiteralExpressionImpl" && exprClass != "JSReferenceExpressionImpl") return null

        val argList = expr.parent ?: return null
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

        val arguments = getArguments(argList)
        val exprIndex = arguments.indexOf(expr)
        if (exprIndex !in 0 until MAX_ARG_SCAN) return null
        if (config.eventArgIndex != null && config.eventArgIndex != exprIndex) return null

        val eventName = resolveToEventName(expr) ?: return null
        val pairedConfigs = if (isEmit) onConfigs else emitConfigs

        val project = sourceElement.project
        val scope = GlobalSearchScope.projectScope(project)
        val psiManager = PsiManager.getInstance(project)
        val targets = mutableListOf<PsiElement>()

        for (ext in listOf("js", "ts", "jsx", "tsx", "vue")) {
            for (vFile in FilenameIndex.getAllFilesByExt(project, ext, scope)) {
                val psiFile = psiManager.findFile(vFile) ?: continue
                collectPairedCalls(psiFile, eventName, pairedConfigs) { matchedArg, pairedMethod ->
                    targets.add(EmitterPsiElementWrapper(matchedArg, eventName, pairedMethod))
                }
            }
        }

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

    /**
     * 將參數解析為 event name 字串：
     * - JSLiteralExpressionImpl: 直接取字串值
     * - JSReferenceExpressionImpl: 嘗試追蹤到 const/let 宣告的字串 initializer
     */
    private fun resolveToEventName(element: PsiElement): String? {
        return when (element::class.simpleName) {
            "JSLiteralExpressionImpl" -> element.text.asStringLiteralValue()
            "JSReferenceExpressionImpl" -> {
                // 只處理簡單識別符（排除 obj.key 這類 qualified reference）
                val isSimpleRef = element.children.none { child ->
                    val n = child::class.simpleName ?: ""
                    n != "LeafPsiElement" && n != "PsiWhiteSpaceImpl"
                }
                if (!isSimpleRef) return null

                val resolved = element.references.firstOrNull()?.resolve() ?: return null
                // 在變數宣告節點中找字串 literal 的 initializer
                resolved.children
                    .firstOrNull { it::class.simpleName == "JSLiteralExpressionImpl" }
                    ?.text?.asStringLiteralValue()
            }
            else -> null
        }
    }

    private fun String.asStringLiteralValue(): String? = when {
        length >= 2 && startsWith("\"") && endsWith("\"") -> substring(1, length - 1)
        length >= 2 && startsWith("'") && endsWith("'") -> substring(1, length - 1)
        else -> null
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

                val matchedArg = if (config.eventArgIndex != null) {
                    arguments.getOrNull(config.eventArgIndex)?.takeIf { arg ->
                        resolveToEventName(arg) == eventName
                    }
                } else {
                    arguments.take(MAX_ARG_SCAN).firstOrNull { arg ->
                        resolveToEventName(arg) == eventName
                    }
                }

                if (matchedArg != null) consumer(matchedArg, name)
            }
        }
        root.children.forEach { collectPairedCalls(it, eventName, pairedConfigs, consumer) }
    }
}