/**
 * This class is used to extract the namespace of a query for the Java Driver.
 */

package com.mongodb.jbplugin.dialects.javadriver.glossary

import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil.*
import com.mongodb.jbplugin.mql.Namespace

private typealias FoundAssignedPsiFields = List<Pair<AssignmentConcept, PsiField>>

@Suppress("ktlint") // it seems the class is too complex for ktlint to understand it
object NamespaceExtractor {
    fun extractNamespace(query: PsiElement): Namespace? {
        val currentClass = query.findContainingClass()
        val queryCollectionRef = query.findMongoDbCollectionReference() ?: return null

        val allMethodCallsInMethod = queryCollectionRef.collectTypeUntil(
            PsiMethodCallExpression::class.java,
            PsiMethod::class.java
        ) + findChildrenOfType(query, PsiMethodCallExpression::class.java)

        val referencesToMongoDbClasses =
            allMethodCallsInMethod.mapNotNull {
                it.findCurrentReferenceToMongoDbObject()
            }.distinct() + listOfNotNull(queryCollectionRef.reference)

        val constructorAssignmentFromConstructorRefs: List<FieldAndConstructorAssignment> =
            referencesToMongoDbClasses.flatMap { ref ->
                val resolution = ref.resolve() ?: return@flatMap emptyList()

                when (resolution) {
                    // we assume constructor injection
                    // find in the constructor how it's defined
                    is PsiField -> {
                        return@flatMap resolveConstructorArgumentReferencesForField(
                            resolution.findContainingClass(),
                            Pair(null, resolution),
                        )
                    }
                    // this can be either a chain call of methods, like getDatabase(..).getCollection()
                    // with constant values or references to fields
                    is PsiMethod -> {
                        val innerMethodCalls = findChildrenOfType(
                            resolution,
                            PsiMethodCallExpression::class.java
                        )
                        val resolutions =
                            innerMethodCalls
                                .filter {
                                    it.type?.isMongoDbClass(it.project) == true
                                }.mapNotNull {
                                    runCatching {
                                        extractRelevantFieldsFromChain(it)
                                    }.getOrNull()
                                }.flatten()
                                .distinctBy { it.first }

                        val containingClass = resolution.findContainingClass()
                        return@flatMap resolutions.flatMap {
                            resolveConstructorArgumentReferencesForField(containingClass, it)
                        }
                    }

                    else ->
                        return@flatMap emptyList()
                }
            }

        val constructorAssignmentFromMethodsRefs: List<FieldAndConstructorAssignment> = allMethodCallsInMethod
            .mapNotNull {
                // if we can remove the namespace from this call, return directly
                // we don't need to traverse the tree anymore
                val maybeNamespace = runCatching { extractNamespaceFromDriverConfigurationMethodChain(it) }
                    .getOrNull()
                if (maybeNamespace != null) {
                    return maybeNamespace
                }
                it.findMongoDbClassReference(it.project)
            }.flatMap {
                when (it.reference?.resolve()) {
                    is PsiField ->
                        resolveConstructorArgumentReferencesForField(
                            currentClass,
                            Pair(null, it.reference?.resolve() as PsiField),
                        )

                    is PsiMethod -> {
                        val method = it.reference?.resolve() as PsiMethod
                        if (method.containingClass?.isMongoDbClass(method.project) == true
                            && it is PsiMethodCallExpression
                        ) {
                            return extractNamespaceFromDriverConfigurationMethodChain(
                                it
                            )
                        }
                        val allInnerExpressions = findChildrenOfAnyType(
                            method,
                            PsiMethodCallExpression::class.java,
                            PsiExpression::class.java
                        )

                        val foundNamespace = allInnerExpressions
                            .filterIsInstance<PsiMethodCallExpression>()
                            .firstNotNullOfOrNull {
                                extractNamespaceFromDriverConfigurationMethodChain(it)
                            }

                        if (foundNamespace != null) {
                            return foundNamespace
                        }

                        emptyList()
                    }

                    else -> emptyList()
                }
            }

        val constructorAssignments = constructorAssignmentFromConstructorRefs + constructorAssignmentFromMethodsRefs
        // at this point, we need to resolve fields or parameters that are not known yet
        // but might be resolvable through the actual class or the abstract class
        val resolvedScopes =
            constructorAssignments.mapNotNull { assignment ->
                currentClass.constructors.firstNotNullOfOrNull {
                    if (assignment.parameter != null) {
                        val callToSuperConstructor = getCallToSuperConstructor(it, constructorAssignments)

                        val indexOfParameter =
                            assignment.constructor.parameterList.getParameterIndex(
                                assignment.parameter,
                            )

                        runCatching {
                            Pair(
                                assignment.concept,
                                callToSuperConstructor!!.argumentList.expressions[indexOfParameter],
                            )
                        }.getOrNull()
                    } else {
                        Pair(assignment.concept, assignment.resolutionExpression)
                    }
                }
            }

        val collection = resolvedScopes.find { it.first == AssignmentConcept.COLLECTION }
        val database = resolvedScopes.find { it.first == AssignmentConcept.DATABASE }
        val client = resolvedScopes.find { it.first == AssignmentConcept.CLIENT }

        if (collection != null && database == null) {
            // if we have a parameter for the collection, but we don't have the database
            // assume it's a call to getDatabase().getCollection()
            return extractNamespaceFromDriverConfigurationMethodChain(collection.second as PsiMethodCallExpression)
        } else if (collection != null && database != null) {
            // if we have a parameter for a collection and database, try to resolve them either
            // from the parent constructor or the actual constructor
            val databaseString = database.second.tryToResolveAsConstantString()!!
            val collectionString = collection.second.tryToResolveAsConstantString()!!
            return Namespace(databaseString, collectionString)
        } else if (client != null || resolvedScopes.size == 1) {
            // if it's not a client and there is only one resolved variable
            // guess from the actual constructor
            val mongodbNamespaceDriverExpression =
                currentClass.constructors.firstNotNullOfOrNull {
                    val callToSuperConstructor =
                        findChildrenOfType(it, PsiMethodCallExpression::class.java).first {
                            it.methodExpression.text == "super" &&
                                    it.methodExpression.resolve() == constructorAssignments.first().constructor
                        }

                    val indexOfParameter =
                        constructorAssignments.first().constructor.parameterList.getParameterIndex(
                            constructorAssignments.first().parameter!!,
                        )
                    callToSuperConstructor.argumentList.expressions[indexOfParameter]
                }

            return extractNamespaceFromDriverConfigurationMethodChain(
                mongodbNamespaceDriverExpression as PsiMethodCallExpression,
            )
        }

        return null
    }

    /**
     * Returns all the relevant assignments and fields that are target of the received
     * field
     *
     * Example input:
     *
     * containingClass = PsiClass:MyRepository
     * field = Pair(AssignmentConcept.COLLECTION, PsiField:myCollection)
     *
     * Example output:
     * listOf(
     *  FieldAndConstructorAssignment(
     *      concept = AssignmentCollection.COLLECTION,
     *      field = PsiField: myCollection,
     *      constructor = PsiMethod: MyRepository(MongoCollection),
     *      parameter = PsiParameter:MongoCollection@index 0,
     *      resolutionExpression = null, // because it's an assignment without any additional logic
     * ))
     *
     */
    private fun resolveConstructorArgumentReferencesForField(
        containingClass: PsiClass,
        field: Pair<AssignmentConcept?, PsiField>,
    ): List<FieldAndConstructorAssignment> {
        return containingClass.constructors.flatMap { constructor ->
            val assignments =
                findChildrenOfType(constructor, PsiAssignmentExpression::class.java)
            val fieldAssignment =
                assignments.find { assignment ->
                    assignment.lExpression.reference?.resolve() == field.second
                }
            fieldAssignment?.let {
                val assignmentConcept =
                    field.first
                        ?: fieldAssignment.type.guessAssignmentConcept(fieldAssignment.project)
                        ?: return emptyList()
                val asParameter = fieldAssignment.rExpression?.reference?.resolve() as? PsiParameter
                asParameter?.let {
                    listOf(
                        FieldAndConstructorAssignment(
                            assignmentConcept,
                            field.second,
                            constructor,
                            asParameter,
                            null,
                        ),
                    )
                } ?: run {
                    // extract from chain
                    val foundAssignments =
                        extractRelevantAssignments(
                            constructor,
                            fieldAssignment.rExpression as PsiMethodCallExpression,
                        )
                    foundAssignments.ifEmpty {
                        listOf(
                            FieldAndConstructorAssignment(
                                assignmentConcept,
                                field.second,
                                constructor,
                                null,
                                fieldAssignment.rExpression,
                            ),
                        )
                    }
                }
            } ?: emptyList()
        }
    }

    /**
     * Gets the relevant call to the super constructor within the current
     * constructor, if it's compatible with the current constructor signature,
     * based on the field assignments.
     */
    private fun getCallToSuperConstructor(
        currentConstructor: PsiMethod?,
        constructorAssignments: List<FieldAndConstructorAssignment>,
    ): PsiMethodCallExpression? {
        return findChildrenOfType(currentConstructor, PsiMethodCallExpression::class.java).firstOrNull {
            it.methodExpression.text == "super" &&
                    it.methodExpression.resolve() == constructorAssignments.first().constructor
        }
    }

    /**
     * Extracts the namespace from a chain of calls like:
     * ```kotlin
     * client.getDatabase(...).getCollection(...)
     * ```
     */
    private fun extractNamespaceFromDriverConfigurationMethodChain(callExpr: PsiMethodCallExpression): Namespace? {
        val returnsCollection = callExpr.type?.isMongoDbCollectionClass(callExpr.project) == true
        val collection: String? =
            if (returnsCollection) {
                callExpr.argumentList.expressions[0].tryToResolveAsConstantString()
            } else {
                null
            }

        val dbExpression =
            if (callExpr.type?.isMongoDbDatabaseClass(callExpr.project) == true) {
                callExpr
            } else if (callExpr.methodExpression.qualifierExpression
                    ?.type
                    ?.isMongoDbDatabaseClass(callExpr.project) == true
            ) {
                callExpr.methodExpression.qualifierExpression as PsiMethodCallExpression?
            } else {
                null
            }

        val database: String? =
            dbExpression?.let {
                dbExpression.argumentList.expressions[0].tryToResolveAsConstantString()
            }

        if (database == null || collection == null) {
            return null
        }

        return Namespace(database, collection)
    }

    /**
     * Extract all the field assignments from a constructor that are relevant
     * for the current method call expression. For example, if we have the following
     * callExpr:
     *
     * ```kotlin
     * client.getDatabase(...).getCollection(...)
     * ```
     *
     * The only relevant field to extract is client from the constructor.
     */
    private fun extractRelevantAssignments(
        constructor: PsiMethod,
        callExpr: PsiMethodCallExpression,
    ): List<FieldAndConstructorAssignment> {
        val result = mutableListOf<FieldAndConstructorAssignment>()
        val returnsCollection = callExpr.type?.isMongoDbCollectionClass(callExpr.project) == true
        if (returnsCollection) {
            val parameter =
                callExpr.argumentList.expressions[0]
                    .reference
                    ?.resolve() as? PsiParameter
            parameter?.let {
                result.add(
                    FieldAndConstructorAssignment(
                        AssignmentConcept.COLLECTION,
                        null,
                        constructor,
                        parameter,
                        null,
                    ),
                )
            }
        }

        val dbExpression =
            if (callExpr.type?.isMongoDbDatabaseClass(callExpr.project) == true) {
                callExpr
            } else if (callExpr.methodExpression.qualifierExpression
                    ?.type
                    ?.isMongoDbDatabaseClass(callExpr.project) == true
            ) {
                callExpr.methodExpression.qualifierExpression as PsiMethodCallExpression?
            } else {
                null
            }

        dbExpression?.let {
            val parameter =
                dbExpression.argumentList.expressions[0]
                    .reference
                    ?.resolve() as? PsiParameter
            parameter?.let {
                result.add(
                    FieldAndConstructorAssignment(
                        AssignmentConcept.DATABASE,
                        null,
                        constructor,
                        parameter,
                        null,
                    ),
                )
            }
        }

        return result
    }

    private fun extractRelevantFieldsFromChain(callExpr: PsiMethodCallExpression): FoundAssignedPsiFields {
        val result = mutableListOf<Pair<AssignmentConcept, PsiField>>()
        val returnsCollection = callExpr.type?.isMongoDbCollectionClass(callExpr.project) == true
        if (returnsCollection) {
            val field =
                callExpr.argumentList.expressions[0]
                    .reference
                    ?.resolve() as? PsiField
            field?.let {
                result.add(Pair(AssignmentConcept.COLLECTION, field))
            }
        }

        val dbExpression =
            if (callExpr.type?.isMongoDbDatabaseClass(callExpr.project) == true) {
                callExpr
            } else if (callExpr.methodExpression.qualifierExpression
                    ?.type
                    ?.isMongoDbDatabaseClass(callExpr.project) == true
            ) {
                callExpr.methodExpression.qualifierExpression as PsiMethodCallExpression?
            } else {
                null
            }

        dbExpression?.let {
            val field =
                dbExpression.argumentList.expressions[0]
                    .reference
                    ?.resolve() as? PsiField
            field?.let {
                result.add(Pair(AssignmentConcept.DATABASE, field))
            }
        }

        return result
    }
}

private enum class AssignmentConcept {
    CLIENT,
    DATABASE,
    COLLECTION,
;
}

/**
 * @property concept
 * @property field
 * @property constructor
 * @property parameter
 * @property resolutionExpression
 */
private data class FieldAndConstructorAssignment(
    val concept: AssignmentConcept,
    val field: PsiField?,
    val constructor: PsiMethod,
    val parameter: PsiParameter?,
    val resolutionExpression: PsiExpression?,
)

private fun PsiType?.guessAssignmentConcept(project: Project): AssignmentConcept? {
    this ?: return null

    return if (isMongoDbClientClass(project)) {
        AssignmentConcept.CLIENT
    } else if (isMongoDbDatabaseClass(project)) {
        AssignmentConcept.DATABASE
    } else if (isMongoDbCollectionClass(project)) {
        AssignmentConcept.COLLECTION
    } else {
        null
    }
}
