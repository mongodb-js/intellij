package com.mongodb.jbplugin.autocomplete

import com.intellij.codeInsight.completion.*
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.ElementPatternCondition
import com.intellij.patterns.InitialPatternCondition
import com.intellij.patterns.PsiJavaPatterns.psiElement
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.util.parentOfType
import com.intellij.util.ProcessingContext
import com.mongodb.jbplugin.accessadapter.datagrip.DataGripBasedReadModelProvider
import com.mongodb.jbplugin.autocomplete.MongoDbElementPatterns.isConnected
import com.mongodb.jbplugin.autocomplete.MongoDbElementPatterns.toLookupElement
import com.mongodb.jbplugin.dialects.javadriver.glossary.tryToResolveAsConstantString
import com.mongodb.jbplugin.dialects.springcriteria.QueryTargetCollectionExtractor
import com.mongodb.jbplugin.dialects.springcriteria.SpringCriteriaDialect
import com.mongodb.jbplugin.editor.dataSource
import com.mongodb.jbplugin.editor.database
import com.mongodb.jbplugin.mql.Namespace

/**
 * This class connects our completion engine with IntelliJ's systems.
 */
class SpringCriteriaCompletionContributor : CompletionContributor() {
    private object Field {
        val place: ElementPattern<PsiElement> =
            psiElement()
                .and(isConnected())
                .and(canBeFieldName())

        object Provider : CompletionProvider<CompletionParameters>() {
            override fun addCompletions(
                parameters: CompletionParameters,
                context: ProcessingContext,
                result: CompletionResultSet,
            ) {
                val dataSource = parameters.originalFile.dataSource ?: return
                val database = parameters.originalFile.database ?: return

                val collection = QueryTargetCollectionExtractor.extractCollection(
                    parameters.position
                ) ?: return

                val readModelProvider =
                    parameters.originalFile.project.getService(
                        DataGripBasedReadModelProvider::class.java,
                    )

                val completions =
                    Autocompletion.autocompleteFields(
                        dataSource,
                        readModelProvider,
                        Namespace(database, collection),
                    ) as? AutocompletionResult.Successful

                val lookupEntries = completions?.entries?.map { it.toLookupElement(SpringCriteriaDialect) }
                result.addAllElements(lookupEntries ?: emptyList())
            }
        }

        fun canBeFieldName(): ElementPattern<PsiElement> =
            object : ElementPattern<PsiElement> {
                override fun accepts(element: Any?) = isFieldName((element as? PsiElement))

                override fun accepts(
                    element: Any?,
                    context: ProcessingContext?,
                ) = isFieldName((element as? PsiElement))

                override fun getCondition(): ElementPatternCondition<PsiElement> =
                    ElementPatternCondition(
                        object : InitialPatternCondition<PsiElement>(PsiElement::class.java) {
                        },
                    )

                private fun isFieldName(element: PsiElement?): Boolean {
                    element ?: return false
                    val isString = element.parentOfType<PsiLiteralExpression>()?.tryToResolveAsConstantString() != null

                    return isString
                }
            }
    }

    private object Collection {
        private const val DOCUMENT_FQN = "org.springframework.data.mongodb.core.mapping.Document"
        val place: ElementPattern<PsiElement> =
            psiElement()
                .and(isConnected())
                .and(canBeCollection())

        object Provider : CompletionProvider<CompletionParameters>() {
            override fun addCompletions(
                parameters: CompletionParameters,
                context: ProcessingContext,
                result: CompletionResultSet,
            ) {
                val dataSource = parameters.originalFile.dataSource ?: return
                val database = parameters.originalFile.database ?: return

                val readModelProvider =
                    parameters.originalFile.project.getService(
                        DataGripBasedReadModelProvider::class.java,
                    )

                val completions =
                    Autocompletion.autocompleteCollections(
                        dataSource,
                        readModelProvider,
                        database,
                    ) as? AutocompletionResult.Successful

                val lookupEntries = completions?.entries?.map { it.toLookupElement(SpringCriteriaDialect) }
                result.addAllElements(lookupEntries ?: emptyList())
            }
        }

        fun canBeCollection(): ElementPattern<PsiElement> =
            object : ElementPattern<PsiElement> {
                override fun accepts(element: Any?) = isCollectionName((element as? PsiElement))

                override fun accepts(
                    element: Any?,
                    context: ProcessingContext?,
                ) = isCollectionName((element as? PsiElement))

                override fun getCondition(): ElementPatternCondition<PsiElement> =
                    ElementPatternCondition(
                        object : InitialPatternCondition<PsiElement>(PsiElement::class.java) {
                        },
                    )

                private fun isCollectionName(element: PsiElement?): Boolean {
                    element ?: return false
                    val docAnnotation = element.parentOfType<PsiAnnotation>() ?: return false
                    return docAnnotation.hasQualifiedName(DOCUMENT_FQN)
                }
            }
    }

    init {
        extend(CompletionType.BASIC, Field.place, Field.Provider)
        extend(CompletionType.BASIC, Collection.place, Collection.Provider)
    }
}
