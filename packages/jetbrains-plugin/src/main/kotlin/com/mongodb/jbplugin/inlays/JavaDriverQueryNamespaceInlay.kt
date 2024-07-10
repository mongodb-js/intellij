package com.mongodb.jbplugin.inlays

import com.intellij.codeInsight.hints.declarative.*
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.util.parentOfType
import com.mongodb.jbplugin.dialects.javadriver.glossary.NamespaceExtractor
import com.mongodb.jbplugin.dialects.javadriver.glossary.findContainingClass
import com.mongodb.jbplugin.dialects.javadriver.glossary.isMongoDbCollectionClass

/**
 * This inlay shows for the current query in which namespace is going to run, if possible,
 * according to the extraction rules of NamespaceExtractor.
 *
 * @see NamespaceExtractor
 */
class JavaDriverQueryNamespaceInlay : InlayHintsProvider {
    override fun createCollector(
        file: PsiFile,
        editor: Editor,
    ): InlayHintsCollector = QueryNamespaceInlayHintsCollector()

    class QueryNamespaceInlayHintsCollector : SharedBypassCollector {
        override fun collectFromElement(
            element: PsiElement,
            sink: InlayTreeSink,
        ) {
            val asMethodCall = element as? PsiMethodCallExpression

            val callsAcollection =
                asMethodCall
                    ?.methodExpression
                    ?.resolve()
                    ?.findContainingClass()
                    ?.isMongoDbCollectionClass(
                        element.project,
                    ) == true

            val callsSuperClass =
                asMethodCall?.findContainingClass() != null &&
                    asMethodCall.findContainingClass().superClass != null &&
                    (asMethodCall.methodExpression.resolve() as? PsiMethod)?.containingClass ==
                    asMethodCall.findContainingClass().superClass

            if (callsAcollection || callsSuperClass) {
                val namespace =
                    runCatching {
                        NamespaceExtractor.extractNamespace(element)
                            ?: NamespaceExtractor.extractNamespace(element.parentOfType<PsiMethod>()!!)
                    }.getOrNull()

                namespace ?: return

                val documentManager = PsiDocumentManager.getInstance(element.project)
                val document = documentManager.getDocument(element.containingFile)!!
                val lineOfElement = document.getLineNumber(element.textOffset)

                sink.addPresentation(
                    EndOfLinePosition(lineOfElement),
                    emptyList(),
                    "Inferred MongoDB namespace for this query.",
                    true,
                ) {
                    text(namespace.toString())
                }
            }
        }
    }
}
