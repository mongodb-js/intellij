/**
 * This class is used to extract the namespace of a query for the Java Driver.
 */

package com.mongodb.jbplugin.dialects.javadriver.glossary

import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScopes
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.findParentOfType
import com.mongodb.jbplugin.mql.Namespace
import com.mongodb.jbplugin.mql.adt.Either
import com.mongodb.jbplugin.mql.components.HasCollectionReference

object NamespaceExtractor {
    fun extractNamespace(query: PsiElement): HasCollectionReference<PsiElement> {
        val currentClass = query.findContainingClass()
        val queryCollectionRef = query.findMongoDbCollectionReference() ?: query

        val setupMethods = findAllCandidateSetupMethodsForQuery(queryCollectionRef)
        val resolvableConcepts = setupMethods.flatMap {
            findAllResolvableConceptsInMethod(it)
        }.distinctBy { (it.concept to it.usagePoint.textOffset) }

        val fillingExpressions = resolvableConcepts.mapNotNull { resolvable ->
            val element = findFillingExpressionForGivenResolvable(resolvable, currentClass)
            if (element != null && element.tryToResolveAsConstantString() != null) {
                resolvable to element
            } else {
                null
            }
        }

        val database = fillingExpressions.firstOrNull {
            it.first.concept ==
                AssignmentConcept.DATABASE
        }
        val collection = fillingExpressions.firstOrNull {
            it.first.concept ==
                AssignmentConcept.COLLECTION
        }

        if (database == null && collection != null) {
            return HasCollectionReference(
                HasCollectionReference.OnlyCollection(
                    collection.first.usagePoint,
                    collection.second.tryToResolveAsConstantString()!!
                )
            )
        }

        if (database != null && collection != null) {
            return HasCollectionReference(
                HasCollectionReference.Known(
                    database.first.usagePoint,
                    collection.first.usagePoint,
                    Namespace(
                        database.second.tryToResolveAsConstantString()!!,
                        collection.second.tryToResolveAsConstantString()!!,
                    )
                )
            )
        }

        return HasCollectionReference(
            HasCollectionReference.Unknown.cast(),
        )
    }

    /**
     * Given a resolvable, find the references upwards to that resolvable for our current
     * class. If we have a fan out inheritance chain, which is typical with repositories
     * (for example, a BaseDao con have multiple implementations) we only care for now
     * on our class.
     */
    private fun findFillingExpressionForGivenResolvable(
        resolvable: Resolvable,
        currentClass: PsiClass
    ): Either<PsiElement, PsiReference>? {
        val maxReferenceDepth = 50

        if (resolvable.inputReference == null) {
            val asConstant = resolvable.usagePoint.tryToResolveAsConstant()
            return if (asConstant.first) Either.left(resolvable.usagePoint) else null
        }

        var currentRef: Either<PsiElement, PsiReference> = resolvable.inputReference
        var previousRef: Either<PsiElement, PsiReference>? = null

        var depth = 0
        // we do this in case we have a too complex recursive graph, we will just return null
        while (depth < maxReferenceDepth) {
            depth++

            if (currentRef is Either.Left) {
                return currentRef
            }

            val referencedElement = (currentRef as Either.Right).value.resolve()
            val nextReference = when (referencedElement) {
                is PsiParameter -> findTopmostCallingReferenceToParameter(
                    referencedElement,
                    currentClass
                )
                is PsiField -> if (referencedElement.initializer != null) {
                    referencedElement.initializer?.reference?.let { Either.right(it) }
                } else {
                    findReferenceThatFillsField(referencedElement, currentClass)
                }
                else -> referencedElement?.reference?.let { Either.right(it) }
            }

            if (nextReference != null) {
                previousRef = currentRef
                currentRef = nextReference
            } else {
                break
            }
        }

        if (depth >= maxReferenceDepth) {
            return null
        }

        return when (currentRef) {
            is Either.Right ->
                if (!doShareHierarchy(
                        currentRef.value.element.findContainingClass(),
                        currentClass
                    )
                ) {
                    Either.Left(currentRef.value.element) as Either<PsiElement, PsiReference>
                } else if (currentRef.value.element.findContainingClass() == currentClass) {
                    Either.Left(currentRef.value.element) as Either<PsiElement, PsiReference>
                } else {
                    null
                }
            else -> currentRef
        }
    }

    private fun findReferenceThatFillsField(psiField: PsiField, currentClass: PsiClass): Either<PsiElement, PsiReference>? {
        val constructorsExposingField = findAllConstructorsExposingField(psiField)

        // for each constructor, resolve the parameter for this field
        val allParamRefs = constructorsExposingField.mapNotNull {
            val assignment = it.findAllChildrenOfType(PsiAssignmentExpression::class.java).find {
                it.lExpression.reference?.resolve() == psiField
            }

            val parameter = assignment?.rExpression?.reference?.resolve() as? PsiParameter
            if (parameter != null) {
                findTopmostCallingReferenceToParameter(parameter, currentClass)
            } else {
                null
            }
        }

        return allParamRefs.firstOrNull()
    }

    private fun findAllConstructorsExposingField(psiField: PsiField): List<PsiMethod> =
        psiField.containingClass?.constructors?.filter {
            it.findAllChildrenOfType(PsiAssignmentExpression::class.java).any {
                it.lExpression.reference?.resolve() == psiField
            } ||
                it.body?.text?.contains("this(") == true
        } ?: emptyList()

    private fun findTopmostCallingReferenceToParameter(psiParameter: PsiParameter, currentClass: PsiClass): Either<PsiElement, PsiReference>? {
        val method = psiParameter.findContainingMethod() ?: return null

        val searchParams = ReferencesSearch.SearchParameters(
            method,
            GlobalSearchScopes.projectProductionScope(currentClass.project),
            false
        )

        val methodCalls = ReferencesSearch.search(searchParams).findAll().mapNotNull {
            it.element.parent
        }.filterIsInstance<PsiMethodCallExpression>()
        // from these method calls we know which argument is relevant for us
        // we can just gather it by index and check if we can resolve it
        val paramIndex = psiParameter.index
        return methodCalls.firstNotNullOfOrNull { methodCall ->
            methodCall.argumentList.expressions.getOrNull(paramIndex)?.let {
                if (it.reference == null &&
                    doesClassInherit(it.findContainingClass(), currentClass)
                ) {
                    Either.left(it)
                } else if (doesClassInherit(
                        it.reference?.element?.findContainingClass(),
                        currentClass
                    )
                ) {
                    Either.right(it.reference!!)
                } else {
                    null
                }
            }
        }
    }

    /**
     * Find all candidate external methods that can be used to set up the query context
     * (database / collection)
     */
    private fun findAllCandidateSetupMethodsForQuery(query: PsiElement): Set<PsiMethod> {
        val currentClass = query.findContainingClass()

        // we are always a candidate for setting up the query context
        val currentMethod = query.findContainingMethod() ?: return emptySet()

        // first, we assume that all our constructors are relevant
        val constructors = currentMethod.findContainingClass().constructorsWithSuperClasses.toSet()

        // now, we need to find out references to other method that might return a mongodb type,
        // strings, or are from a super class
        val allMethods = currentMethod.findAllChildrenOfType(PsiMethodCallExpression::class.java)
            .filter {
                it.type?.isMongoDbCollectionClass(query.project) == true ||
                    it.type?.isMongoDbDatabaseClass(query.project) == true ||
                    it.type?.canonicalText?.endsWith("String") == true ||
                    (
                        it.fuzzyResolveMethod() != null &&
                            doesClassInherit(
                                it.fuzzyResolveMethod()?.containingClass!!,
                                currentClass
                            )
                        )
            }.mapNotNull {
                it.fuzzyResolveMethod()
            }.toSet()

        val allTransitiveMethods = allMethods.flatMap {
            it.findAllChildrenOfType(PsiMethodCallExpression::class.java)
        }.flatMap {
            val method = it.resolveMethod() ?: return@flatMap emptySet()
            if (method.containingClass == currentClass ||
                doesClassInherit(method.containingClass!!, currentClass)
            ) {
                setOf(method)
            } else {
                emptySet()
            }
        }

        return (constructors + allMethods + currentMethod + allTransitiveMethods).distinctBy {
            it.textOffset
        }.toSet()
    }

    /**
     * For a given method (either a constructor, the method itself, overrides...) returns
     * the list of references to either collections or databases for the MongoDB driver
     */
    private fun findAllResolvableConceptsInMethod(method: PsiMethod): Set<Resolvable> {
        return method.findAllChildrenOfType(PsiExpression::class.java)
            .filter {
                it.type?.isMongoDbClass(method.project) == true
            }.flatMap {
                when (it) {
                    is PsiMethodCallExpression -> {
                        val method = it.fuzzyResolveMethod()
                        if (method == null) {
                            return@flatMap emptySet<Resolvable>()
                        }

                        if (it.argumentList.expressions.size == 0) { // unknown method, so go inside
                            return@flatMap findAllResolvableConceptsInMethod(method)
                        }

                        val arg = it.argumentList.expressions.firstOrNull {
                            it.type?.canonicalText?.endsWith("String") == true
                        } ?: return@flatMap emptyList()

                        when (method.name) {
                            "getCollection" -> listOf(
                                Resolvable(
                                    AssignmentConcept.COLLECTION,
                                    arg,
                                    arg.reference?.let {
                                        Either.right(it)
                                    }
                                )
                            )
                            "getDatabase" -> listOf(
                                Resolvable(
                                    AssignmentConcept.DATABASE,
                                    arg,
                                    arg.reference?.let { Either.right(it) }
                                )
                            )
                            else -> it.argumentList.expressions.filter {
                                it.reference?.resolve() is PsiParameter
                            }.mapNotNull {
                                val param = it.reference?.resolve() as? PsiParameter
                                when (param?.name) {
                                    "collection" -> Resolvable(
                                        AssignmentConcept.COLLECTION,
                                        it,
                                        it.reference?.let {
                                            Either.right(it)
                                        }
                                    )
                                    "database" -> Resolvable(
                                        AssignmentConcept.DATABASE,
                                        it,
                                        it.reference?.let {
                                            Either.right(it)
                                        }
                                    )
                                    else -> null
                                }
                            }
                        }
                    } else -> {
                        if (it.type?.isMongoDbCollectionClass(method.project) == true) {
                            listOf(
                                Resolvable(
                                    AssignmentConcept.COLLECTION,
                                    it,
                                    it.reference?.let { Either.right(it) }
                                )
                            )
                        } else if (it.type?.isMongoDbDatabaseClass(method.project) == true) {
                            listOf(
                                Resolvable(
                                    AssignmentConcept.DATABASE,
                                    it,
                                    it.reference?.let { Either.right(it) }
                                )
                            )
                        } else {
                            emptyList()
                        }
                    }
                }
            }.toSet()
    }

    private fun doesClassInherit(parent: PsiClass?, child: PsiClass): Boolean {
        if (parent == null) {
            return false
        }

        if (child == parent) {
            return true
        }

        if (child.superClass == null) {
            return false
        }

        return doesClassInherit(parent, child.superClass!!)
    }

    private fun doShareHierarchy(a: PsiClass, b: PsiClass): Boolean {
        fun superClassesOf(current: PsiClass): List<PsiClass> {
            if (current.superClass == null) {
                return emptyList()
            }

            return superClassesOf(current.superClass!!) + current
        }

        val aClasses = superClassesOf(a)
        val bClasses = superClassesOf(b)

        return aClasses.intersect(bClasses).isNotEmpty()
    }

    private fun Either<PsiElement, PsiReference>.tryToResolveAsConstantString(): String? {
        return when (this) {
            is Either.Left -> value.tryToResolveAsConstantString()
            is Either.Right -> value.resolve()?.tryToResolveAsConstantString()
        }
    }
}

private enum class AssignmentConcept {
    DATABASE,
    COLLECTION,
}

private data class Resolvable(
    val concept: AssignmentConcept,
    val usagePoint: PsiExpression,
    val inputReference: Either<PsiElement, PsiReference>?
)

private val PsiParameter.index: Int
    get() = findContainingMethod()!!.parameterList.getParameterIndex(this)

private fun PsiElement.findContainingMethod(): PsiMethod? =
    findParentOfType<PsiMethod>()

private val PsiClass.constructorsWithSuperClasses: List<PsiMethod>
    get() = constructors.toList() +
        (superClass?.constructorsWithSuperClasses ?: emptyList<PsiMethod>())
