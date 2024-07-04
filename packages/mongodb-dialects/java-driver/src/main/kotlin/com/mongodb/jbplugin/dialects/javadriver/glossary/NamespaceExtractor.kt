package com.mongodb.jbplugin.dialects.javadriver.glossary

import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.mongodb.jbplugin.dialects.javadriver.glossary.abstractions.*

object NamespaceExtractor {
    data class Namespace(
        val database: String,
        val collection: String,
    )

    fun extractNamespace(query: PsiElement): Namespace? {
        val currentClass = query.findContainingClass()
//        val isAbstractRepository = AbstractRepositoryDaoAbstraction.isIn(query)
//        val constructorInjection = ConstructorInjectionAbstraction.isIn(query)
        val customQueryDsl = CustomQueryDslAbstraction.isIn(query)
//        val driverInFactory = DriverInFactoryMethodAbstraction.isIn(query)
//        val isRepository = RepositoryDaoAbstraction.isIn(query)

        if (customQueryDsl) {
            // we need to traverse to the parent method that contains a reference to the mongodb class
            // as we don't know who is actually calling the mongodb class, we will need to traverse upwards until
            // we find the beginning of the method
            val methodCalls = query.collectTypeUntil(PsiMethodCallExpression::class.java, PsiMethod::class.java)
            val referencesToMongoDbClasses =
                methodCalls.mapNotNull {
                    it.findCurrentReferenceToMongoDbObject()
                }

            val constructorAssignments: List<FieldAndConstructorAssignment> =
                referencesToMongoDbClasses.firstNotNullOf { ref ->
                    val resolution = ref.resolve() ?: return@firstNotNullOf null

                    when (resolution) {
                        // we assume constructor injection
                        // find in the constructor how it's defined
                        is PsiField -> {
                            return@firstNotNullOf resolveConstructorArgumentReferencesForField(
                                resolution.findContainingClass(),
                                Pair(null, resolution),
                            )
                        } is PsiMethod -> {
                            val methodCalls = PsiTreeUtil.findChildrenOfType(resolution, PsiMethodCallExpression::class.java)
                            val resolutions =
                                methodCalls
                                    .filter {
                                        it.type?.isMongoDbClass(it.project) == true
                                    }.mapNotNull {
                                        runCatching {
                                            extractRelevantFieldsFromChain(it)
                                        }.getOrNull()
                                    }.flatten()
                                    .distinctBy { it.first }

                            val containingClass = resolution.findContainingClass()
                            return@firstNotNullOf resolutions.flatMap {
                                resolveConstructorArgumentReferencesForField(containingClass, it)
                            }
                        } else ->
                            return@firstNotNullOf listOf()
                    }
                }

            val resolvedScopes =
                constructorAssignments.map { assignment ->
                    currentClass.constructors.firstNotNullOf {
                        val callToSuperConstructor = getCallToSuperConstructor(it, constructorAssignments)

                        val indexOfParameter =
                            assignment.constructor.parameterList.getParameterIndex(
                                assignment.parameter,
                            )

                        Pair(
                            assignment.concept,
                            callToSuperConstructor!!.argumentList.expressions[indexOfParameter],
                        )
                    }
                }

            val collection = resolvedScopes.find { it.first == AssignmentConcept.COLLECTION }
            val database = resolvedScopes.find { it.first == AssignmentConcept.DATABASE }
            val client = resolvedScopes.find { it.first == AssignmentConcept.CLIENT }

            if (collection != null && database != null) {
                return Namespace(resolveConstant(database.second)!!, resolveConstant(collection.second)!!)
            } else if (client != null || resolvedScopes.size == 1) {
                val mongodbNamespaceDriverExpression =
                    currentClass.constructors.firstNotNullOfOrNull {
                        val callToSuperConstructor =
                            PsiTreeUtil.findChildrenOfType(it, PsiMethodCallExpression::class.java).first {
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
            } else {
                return null
            }
        }

        return null
    }

    private fun resolveConstructorArgumentReferencesForField(
        containingClass: PsiClass,
        field: Pair<AssignmentConcept?, PsiField>,
    ): List<FieldAndConstructorAssignment> {
        return containingClass.constructors.flatMap { constructor ->
            val assignments =
                PsiTreeUtil.findChildrenOfType(constructor, PsiAssignmentExpression::class.java)
            val fieldAssignment =
                assignments.find { assignment ->
                    assignment.lExpression.reference?.resolve() == field.second
                }
            if (fieldAssignment != null) {
                val assignmentConcept =
                    field.first
                        ?: fieldAssignment.type.guessAssignmentConcept(fieldAssignment.project)
                        ?: return emptyList()

                val asParameter = fieldAssignment.rExpression?.reference?.resolve() as? PsiParameter
                if (asParameter == null) {
                    // extract from chain
                    extractRelevantAssignments(
                        constructor,
                        fieldAssignment.rExpression as PsiMethodCallExpression,
                    )
                } else {
                    listOf(
                        FieldAndConstructorAssignment(
                            assignmentConcept,
                            field.second,
                            constructor,
                            asParameter,
                        ),
                    )
                }
            } else {
                emptyList()
            }
        }
    }

    private fun getCallToSuperConstructor(
        it: PsiMethod?,
        constructorAssignments: List<FieldAndConstructorAssignment>,
    ): PsiMethodCallExpression? {
        val callToSuperConstructor =
            PsiTreeUtil.findChildrenOfType(it, PsiMethodCallExpression::class.java).first {
                it.methodExpression.text == "super" &&
                    it.methodExpression.resolve() == constructorAssignments.first().constructor
            }
        return callToSuperConstructor
    }

    private fun resolveConstant(expression: PsiExpression): String? {
        val javaFacade = JavaPsiFacade.getInstance(expression.project)
        val constant = javaFacade.constantEvaluationHelper.computeConstantExpression(expression)
        return constant as? String
    }

    private fun extractNamespaceFromDriverConfigurationMethodChain(callExpr: PsiMethodCallExpression): Namespace {
        val returnsCollection = callExpr.type?.isMongoDbCollectionClass(callExpr.project) == true
        val collection: String? =
            if (returnsCollection) {
                resolveConstant(callExpr.argumentList.expressions[0])
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
            if (dbExpression != null) {
                resolveConstant(dbExpression.argumentList.expressions[0])
            } else {
                null
            }

        return Namespace(database ?: "unk", collection ?: "unk")
    }

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
            if (parameter != null) {
                result.add(FieldAndConstructorAssignment(AssignmentConcept.COLLECTION, null, constructor, parameter))
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

        if (dbExpression != null) {
            val parameter =
                dbExpression.argumentList.expressions[0]
                    .reference
                    ?.resolve() as? PsiParameter
            if (parameter != null) {
                result.add(FieldAndConstructorAssignment(AssignmentConcept.DATABASE, null, constructor, parameter))
            }
        }

        return result
    }

    private fun extractRelevantFieldsFromChain(callExpr: PsiMethodCallExpression): List<Pair<AssignmentConcept, PsiField>> {
        val result = mutableListOf<Pair<AssignmentConcept, PsiField>>()
        val returnsCollection = callExpr.type?.isMongoDbCollectionClass(callExpr.project) == true
        if (returnsCollection) {
            val field =
                callExpr.argumentList.expressions[0]
                    .reference
                    ?.resolve() as? PsiField
            if (field != null) {
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

        if (dbExpression != null) {
            val field =
                dbExpression.argumentList.expressions[0]
                    .reference
                    ?.resolve() as? PsiField
            if (field != null) {
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
}

private fun PsiType?.guessAssignmentConcept(project: Project): AssignmentConcept? {
    if (this == null) return null

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

private data class FieldAndConstructorAssignment(
    val concept: AssignmentConcept,
    val field: PsiField?,
    val constructor: PsiMethod,
    val parameter: PsiParameter,
)
