package com.github.xinxianwu.emitternavigator.navigation

import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.FakePsiElement
import javax.swing.Icon

class EmitterPsiElementWrapper(
    private val original: PsiElement,
    private val eventName: String,
    private val methodName: String
) : FakePsiElement() {

    override fun getParent(): PsiElement = original

    override fun getContainingFile(): PsiFile? = original.containingFile

    override fun navigate(requestFocus: Boolean) {
        (original as? Navigatable)?.navigate(requestFocus)
    }

    override fun canNavigate(): Boolean = (original as? Navigatable)?.canNavigate() ?: false

    override fun canNavigateToSource(): Boolean = (original as? Navigatable)?.canNavigateToSource() ?: false

    override fun getPresentation(): ItemPresentation {
        return object : ItemPresentation {
            override fun getPresentableText(): String {
                val file = original.containingFile
                val virtualFile = file?.virtualFile
                val document = virtualFile?.let { FileDocumentManager.getInstance().getDocument(it) }
                val lineNumber = document?.getLineNumber(original.textOffset)?.plus(1) ?: 0
                
                return "\"$eventName\" in $methodName() at line $lineNumber"
            }

            override fun getLocationString(): String? {
                val file = original.containingFile ?: return null
                val virtualFile = file.virtualFile ?: return null
                val projectBasePath = original.project.basePath
                
                return if (projectBasePath != null && virtualFile.path.startsWith(projectBasePath)) {
                    virtualFile.path.substring(projectBasePath.length).removePrefix("/")
                } else {
                    virtualFile.name
                }
            }

            override fun getIcon(unused: Boolean): Icon? {
                return original.containingFile?.getIcon(0)
            }
        }
    }

    override fun getText(): String = original.text

    override fun getTextRange() = original.textRange
}
