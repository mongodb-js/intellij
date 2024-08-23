package com.mongodb.jbplugin.autocomplete

import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.AutoCompletionPolicy
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.database.dataSource.localDataSource
import com.intellij.openapi.application.ApplicationManager
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.ElementPatternCondition
import com.intellij.patterns.InitialPatternCondition
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.ProcessingContext
import com.mongodb.jbplugin.accessadapter.datagrip.adapter.isConnected
import com.mongodb.jbplugin.dialects.javadriver.glossary.JavaDriverDialect
import com.mongodb.jbplugin.editor.MongoDbVirtualFileDataSourceProvider
import com.mongodb.jbplugin.i18n.Icons
import com.mongodb.jbplugin.observability.probe.AutocompleteSuggestionAcceptedProbe

object MongoDbElementPatterns {
    fun isConnected(): ElementPattern<PsiElement> =
        object : ElementPattern<PsiElement> {
            override fun accepts(element: Any?) = isFileConnected((element as? PsiElement)?.containingFile)

            override fun accepts(
                element: Any?,
                context: ProcessingContext?,
            ) = isFileConnected((element as? PsiElement)?.containingFile)

            override fun getCondition(): ElementPatternCondition<PsiElement> =
                ElementPatternCondition(
                    object : InitialPatternCondition<PsiElement>(PsiElement::class.java) {
                    },
                )

            private fun isFileConnected(psiFile: PsiFile?): Boolean {
                psiFile ?: return false
                psiFile.originalFile.virtualFile ?: return false

                val dbDataSource =
                    MongoDbVirtualFileDataSourceProvider().getDataSource(
                        psiFile.project,
                        psiFile.originalFile.virtualFile,
                    )

                return !(dbDataSource == null || dbDataSource.localDataSource?.isConnected() == false)
            }
        }

    fun AutocompletionEntry.toLookupElement(): LookupElement {
        val lookupElement =
            LookupElementBuilder
                .create(entry)
                .withInsertHandler { _, _ ->
                    val application = ApplicationManager.getApplication()
                    val probe = application.getService(AutocompleteSuggestionAcceptedProbe::class.java)

                    when (this.type) {
                        AutocompletionEntry.AutocompletionEntryType.DATABASE ->
                            probe.databaseCompletionAccepted(JavaDriverDialect)

                        AutocompletionEntry.AutocompletionEntryType.COLLECTION ->
                            probe.collectionCompletionAccepted(JavaDriverDialect)

                        AutocompletionEntry.AutocompletionEntryType.FIELD ->
                            probe.fieldCompletionAccepted(JavaDriverDialect)
                    }
                }
                .withIcon(
                    when (type) {
                        AutocompletionEntry.AutocompletionEntryType.DATABASE -> Icons.databaseAutocompleteEntry
                        AutocompletionEntry.AutocompletionEntryType.COLLECTION -> Icons.collectionAutocompleteEntry
                        AutocompletionEntry.AutocompletionEntryType.FIELD -> Icons.fieldAutocompleteEntry
                    },
                )
                .withTypeText(
                    if (type == AutocompletionEntry.AutocompletionEntryType.FIELD) {
                        JavaDriverDialect.formatter.formatType(bsonType!!)
                    } else {
                        type.presentableName
                    },
                    true,
                )
                .withCaseSensitivity(true)
                .withAutoCompletionPolicy(AutoCompletionPolicy.GIVE_CHANCE_TO_OVERWRITE)

        return PrioritizedLookupElement.withPriority(lookupElement, Double.MAX_VALUE)
    }
}