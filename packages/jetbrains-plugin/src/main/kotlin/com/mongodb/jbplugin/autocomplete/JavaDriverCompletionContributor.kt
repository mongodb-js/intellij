/**
 * This module is responsible for adapting our autocomplete engine to IntelliJ's platform.
 */

package com.mongodb.jbplugin.autocomplete

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.AutoCompletionPolicy
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.database.dataSource.localDataSource
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.ElementPatternCondition
import com.intellij.patterns.InitialPatternCondition
import com.intellij.patterns.PsiJavaPatterns.*
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.util.*
import com.intellij.util.ProcessingContext
import com.mongodb.jbplugin.accessadapter.datagrip.DataGripBasedReadModelProvider
import com.mongodb.jbplugin.accessadapter.datagrip.adapter.isConnected
import com.mongodb.jbplugin.autocomplete.JavaDriverMongoDbElementPatterns.canBeFieldName
import com.mongodb.jbplugin.autocomplete.JavaDriverMongoDbElementPatterns.isCollectionReference
import com.mongodb.jbplugin.autocomplete.JavaDriverMongoDbElementPatterns.isConnected
import com.mongodb.jbplugin.autocomplete.JavaDriverMongoDbElementPatterns.isDatabaseReference
import com.mongodb.jbplugin.dialects.javadriver.glossary.*
import com.mongodb.jbplugin.editor.MongoDbVirtualFileDataSourceProvider
import com.mongodb.jbplugin.editor.dataSource
import com.mongodb.jbplugin.i18n.Icons
import com.mongodb.jbplugin.i18n.Icons.scaledToText

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

private fun AutocompletionEntry.toLookupElement(): LookupElement {
    val lookupElement =
        LookupElementBuilder
            .create(entry)
            .withIcon(Icons.logo.scaledToText())
            .withTypeText(
                if (type == AutocompletionEntry.AutocompletionEntryType.FIELD) {
                    JavaDriverDialect.formatter.formatType(bsonType!!)
                } else {
                    type.presentableName
                },
                true,
            ).withCaseSensitivity(true)
            .withAutoCompletionPolicy(AutoCompletionPolicy.GIVE_CHANCE_TO_OVERWRITE)

    return PrioritizedLookupElement.withPriority(lookupElement, Double.MAX_VALUE)
}
