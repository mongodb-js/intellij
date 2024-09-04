package com.mongodb.jbplugin.inlays

import com.intellij.codeInsight.hints.declarative.*
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.mongodb.jbplugin.dialects.javadriver.glossary.NamespaceExtractor
import com.mongodb.jbplugin.editor.dialect
import com.mongodb.jbplugin.mql.Namespace
import com.mongodb.jbplugin.mql.components.HasCollectionReference

/**
 * This inlay shows for the current query in which namespace is going to run, if possible,
 * according to the extraction rules of NamespaceExtractor.
 *
 * @see NamespaceExtractor
 */
class MongoDbQueryNamespaceInlay : InlayHintsProvider {
    override fun createCollector(
        file: PsiFile,
        editor: Editor,
    ): InlayHintsCollector = QueryNamespaceInlayHintsCollector()

    class QueryNamespaceInlayHintsCollector : SharedBypassCollector {
        override fun collectFromElement(
            element: PsiElement,
            sink: InlayTreeSink,
        ) {
            val dialect = element.containingFile.dialect ?: return
            if (!dialect.parser.isCandidateForQuery(element)) {
                return
            }

            val psiManager = PsiManager.getInstance(element.project)
            val attachment = dialect.parser.attachment(element)
            if (!psiManager.areElementsEquivalent(attachment, element)) {
                return
            }

            val query = dialect.parser.parse(element)
            val collRefComponent = query.component<HasCollectionReference<PsiElement>>() ?: return

            val namespace = when (val collRef = collRefComponent.reference) {
                is HasCollectionReference.Known -> collRef.namespace
                is HasCollectionReference.OnlyCollection -> {
                    val context = dialect.connectionContextExtractor?.gatherContext(element.project)
                    Namespace(context?.database ?: "", collRef.collection)
                }
                else -> {
                    val context = dialect.connectionContextExtractor?.gatherContext(element.project)
                    Namespace(context?.database ?: "", "")
                }
            }

            val documentManager = PsiDocumentManager.getInstance(element.project)
            val document = documentManager.getDocument(element.containingFile)!!
            val lineOfElement = document.getLineNumber(query.source.textOffset)

            sink.addPresentation(
                EndOfLinePosition(lineOfElement),
                emptyList(),
                hasBackground = true,
            ) {
                text(namespace.toString())
            }
        }
    }
}
