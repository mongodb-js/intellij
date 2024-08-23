/**
 * This module is responsible for adapting our autocomplete engine to IntelliJ's platform.
 */

package com.mongodb.jbplugin.autocomplete

import com.intellij.codeInsight.completion.*
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.ElementPatternCondition
import com.intellij.patterns.InitialPatternCondition
import com.intellij.patterns.PsiJavaPatterns.psiElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.util.parentOfType
import com.intellij.util.ProcessingContext
import com.mongodb.jbplugin.accessadapter.datagrip.DataGripBasedReadModelProvider
import com.mongodb.jbplugin.autocomplete.JavaDriverMongoDbElementPatterns.canBeFieldName
import com.mongodb.jbplugin.autocomplete.JavaDriverMongoDbElementPatterns.isCollectionReference
import com.mongodb.jbplugin.autocomplete.JavaDriverMongoDbElementPatterns.isDatabaseReference
import com.mongodb.jbplugin.autocomplete.MongoDbElementPatterns.isConnected
import com.mongodb.jbplugin.autocomplete.MongoDbElementPatterns.toLookupElement
import com.mongodb.jbplugin.dialects.javadriver.glossary.*
import com.mongodb.jbplugin.editor.dataSource
import kotlin.collections.emptyList
import kotlin.collections.map

/**
 * This class connects our completion engine with IntelliJ's systems.
 */
class JavaDriverCompletionContributor : CompletionContributor() {
    init {
        extend(CompletionType.BASIC, Database.place, Database.Provider)
        extend(CompletionType.BASIC, Collection.place, Collection.Provider)
        extend(CompletionType.BASIC, Field.place, Field.Provider)
    }
}

private object Database {
    val place: ElementPattern<PsiElement> =
        psiElement()
            .and(isConnected())
            .and(isDatabaseReference())

    object Provider : CompletionProvider<CompletionParameters>() {
        override fun addCompletions(
            parameters: CompletionParameters,
            context: ProcessingContext,
            result: CompletionResultSet,
        ) {
            val dataSource = parameters.originalFile.dataSource!!
            val readModelProvider =
                parameters.originalFile.project.getService(
                    DataGripBasedReadModelProvider::class.java,
                )

            val completions =
                Autocompletion.autocompleteDatabases(
                    dataSource,
                    readModelProvider,
                ) as? AutocompletionResult.Successful

            val lookupEntries = completions?.entries?.map { it.toLookupElement() } ?: emptyList()
            result.addAllElements(lookupEntries)
        }
    }
}

private object Collection {
    val place: ElementPattern<PsiElement> =
        psiElement()
            .and(isConnected())
            .and(isCollectionReference())

    object Provider : CompletionProvider<CompletionParameters>() {
        override fun addCompletions(
            parameters: CompletionParameters,
            context: ProcessingContext,
            result: CompletionResultSet,
        ) {
            val dataSource = parameters.originalFile.dataSource!!
            val database = NamespaceExtractor.extractNamespace(parameters.originalPosition!!)?.database ?: return
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

            val lookupEntries = completions?.entries?.map { it.toLookupElement() } ?: emptyList()
            result.addAllElements(lookupEntries)
        }
    }
}

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
            val dataSource = parameters.originalFile.dataSource!!
            val currentMethod = parameters.originalPosition?.parentOfType<PsiMethod>() ?: return
            val namespace = NamespaceExtractor.extractNamespace(currentMethod) ?: return
            val readModelProvider =
                parameters.originalFile.project.getService(
                    DataGripBasedReadModelProvider::class.java,
                )

            val completions =
                Autocompletion.autocompleteFields(
                    dataSource,
                    readModelProvider,
                    namespace,
                ) as? AutocompletionResult.Successful

            val lookupEntries = completions?.entries?.map { it.toLookupElement() } ?: emptyList()
            result.addAllElements(lookupEntries)
        }
    }
}

private object JavaDriverMongoDbElementPatterns {
    fun isDatabaseReference(): ElementPattern<PsiElement> =
        object : ElementPattern<PsiElement> {
            override fun accepts(element: Any?) = isDatabaseReference((element as? PsiElement))

            override fun accepts(
                element: Any?,
                context: ProcessingContext?,
            ) = isDatabaseReference((element as? PsiElement))

            override fun getCondition(): ElementPatternCondition<PsiElement> =
                ElementPatternCondition(
                    object : InitialPatternCondition<PsiElement>(PsiElement::class.java) {
                    },
                )

            private fun isDatabaseReference(element: PsiElement?): Boolean {
                element ?: return false
                val refToDb =
                    element
                        .parentOfType<PsiMethodCallExpression>(true)
                        ?.findMongoDbClassReference(element.project)
                        ?: return false

                return refToDb.type?.isMongoDbDatabaseClass(refToDb.project) == true
            }
        }

    fun isCollectionReference(): ElementPattern<PsiElement> =
        object : ElementPattern<PsiElement> {
            override fun accepts(element: Any?) = isCollectionReference((element as? PsiElement))

            override fun accepts(
                element: Any?,
                context: ProcessingContext?,
            ) = isCollectionReference((element as? PsiElement))

            override fun getCondition(): ElementPatternCondition<PsiElement> =
                ElementPatternCondition(
                    object : InitialPatternCondition<PsiElement>(PsiElement::class.java) {
                    },
                )

            private fun isCollectionReference(element: PsiElement?): Boolean {
                element ?: return false
                val refToDb =
                    element
                        .parentOfType<PsiMethodCallExpression>(true)
                        ?.findMongoDbClassReference(element.project)
                        ?: return false

                return refToDb.type?.isMongoDbCollectionClass(refToDb.project) == true
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
                val isInQuery = isInQuery(element)
                val isString = element.parentOfType<PsiLiteralExpression>()?.tryToResolveAsConstantString() != null

                return isInQuery && isString
            }

            private fun isInQuery(element: PsiElement): Boolean {
                val methodCall = element.parentOfType<PsiMethodCallExpression>(false) ?: return false
                val isInQuery = JavaDriverDialect.parser.isCandidateForQuery(methodCall)

                return isInQuery || isInQuery(methodCall)
            }
        }
}