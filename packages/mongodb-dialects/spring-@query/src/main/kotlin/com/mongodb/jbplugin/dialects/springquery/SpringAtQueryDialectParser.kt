package com.mongodb.jbplugin.dialects.springquery

import com.intellij.database.util.common.containsElements
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiNameValuePair
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTypesUtil
import com.intellij.psi.util.elementType
import com.mongodb.jbplugin.dialects.DialectParser
import com.mongodb.jbplugin.dialects.javadriver.glossary.findAllChildrenOfType
import com.mongodb.jbplugin.dialects.javadriver.glossary.findTopParentBy
import com.mongodb.jbplugin.dialects.javadriver.glossary.toBsonType
import com.mongodb.jbplugin.dialects.springcriteria.ModelCollectionExtractor
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.components.HasCollectionReference
import com.mongodb.jbplugin.mql.components.HasFieldReference
import com.mongodb.jbplugin.mql.components.HasFilter
import com.mongodb.jbplugin.mql.components.HasValueReference

object SpringAtQueryDialectParser : DialectParser<PsiElement> {
    override fun isCandidateForQuery(source: PsiElement): Boolean {
        return findParentMethodWithQueryAnnotation(source) != null
    }

    override fun attachment(source: PsiElement): PsiElement {
        return findParentMethodWithQueryAnnotation(source)!!
    }

    override fun parse(source: PsiElement): Node<PsiElement> {
        if (source !is PsiMethod) {
            return Node(source, emptyList())
        }

        val collection = resolveMethodCollection(source)

        val queryAnnotation = source.annotations.find {
            it.hasQualifiedName(QUERY_FQN)
        } ?: return Node(source, listOf(collection))

        val injectedQueryHost = findInjectedQuery(queryAnnotation)
        val injectedQuery =
            injectedQueryHost?.children?.firstOrNull() ?: return Node(source, listOf(collection))

        return Node(
            source,
            listOf(
                collection,
                HasFilter(recursivelyParseJsonFilter(injectedQuery, source))
            )
        )
    }

    private fun recursivelyParseJsonFilter(source: PsiElement, parent: PsiMethod): List<Node<PsiElement>> {
        return when (source.elementType?.toString()) {
            "OBJECT" -> {
                return source.children.flatMap {
                    recursivelyParseJsonFilter(it, parent)
                }
            }
            "PROPERTY" -> {
                val fieldName = resolveToFieldNameReference(source.children[0])
                val valueOrRef = resolveValueReference(source.children[1], parent)

                return listOf(
                    Node(
                        source,
                        listOf(
                            fieldName,
                            valueOrRef
                        )
                    )
                )
            }
            else -> emptyList()
        }
        // MongoDBJsonObjectImpl(OBJECT)
        // MongoDBJsonPropertyImpl(PROPERTY)
        // MongoDBJsonReferenceExpressionImpl(REFERENCE_EXPRESSION)
        // MongoDBJsonNumberLiteralImpl(NUMBER_LITERAL)
        // MongoDBJsonParameterLiteralImpl(PARAMETER_LITERAL)
    }

    private fun resolveToFieldNameReference(fieldName: PsiElement): HasFieldReference<PsiElement> {
        return HasFieldReference(
            HasFieldReference.Known(fieldName, fieldName.text)
        )
    }

    private fun resolveValueReference(valueRef: PsiElement, parent: PsiMethod): HasValueReference<PsiElement> {
        return when (valueRef.elementType.toString()) {
            "PARAMETER_LITERAL" -> {
                val methodArgIdx = valueRef.text.trim('?').toInt()
                val methodArg =
                    parent.parameterList.parameters.getOrNull(methodArgIdx)
                        ?: return HasValueReference(
                            HasValueReference.Unknown as HasValueReference.ValueReference<PsiElement>
                        )

                val argType = methodArg.type.toBsonType()
                return HasValueReference(
                    HasValueReference.Runtime(methodArg, argType)
                )
            } else ->
                return HasValueReference(
                    HasValueReference.Unknown as HasValueReference.ValueReference<PsiElement>
                )
        }
    }

    private fun resolveMethodCollection(method: PsiMethod): HasCollectionReference<PsiElement> {
        val fqnSpringRepositoryInterface = JavaPsiFacade.getInstance(method.project)
            .findClass(
                "org.springframework.data.repository.Repository",
                GlobalSearchScope.allScope(method.project)
            )
            ?: return HasCollectionReference(
                HasCollectionReference.Unknown as HasCollectionReference.CollectionReference<PsiElement>
            )

        val methodClass = method.containingClass ?: return HasCollectionReference(
            HasCollectionReference.Unknown as HasCollectionReference.CollectionReference<PsiElement>
        )

        val repositoryInterface = methodClass.extendsList?.referencedTypes?.find { child ->
            val psiClass = child.resolve() ?: return@find false

            psiClass.isInterface &&
                (
                    psiClass.isInheritorDeep(fqnSpringRepositoryInterface, null) ||
                        psiClass == fqnSpringRepositoryInterface
                    )
        } ?: return HasCollectionReference(
            HasCollectionReference.Unknown as HasCollectionReference.CollectionReference<PsiElement>
        )

        val typeClass =
            repositoryInterface.parameters.getOrNull(0) ?: return HasCollectionReference(
                HasCollectionReference.Unknown as HasCollectionReference.CollectionReference<PsiElement>
            )

        val typeClassPsi = PsiTypesUtil.getPsiClass(typeClass) ?: return HasCollectionReference(
            HasCollectionReference.Unknown as HasCollectionReference.CollectionReference<PsiElement>
        )

        val extractedCollection =
            ModelCollectionExtractor.fromPsiClass(typeClassPsi) ?: return HasCollectionReference(
                HasCollectionReference.Unknown as HasCollectionReference.CollectionReference<PsiElement>
            )

        return HasCollectionReference(
            HasCollectionReference.OnlyCollection(typeClassPsi, extractedCollection)
        )
    }

    override fun isReferenceToDatabase(source: PsiElement): Boolean {
        return false
    }

    override fun isReferenceToCollection(source: PsiElement): Boolean {
        return false
    }

    override fun isReferenceToField(source: PsiElement): Boolean {
        return false
    }

    private fun findParentMethodWithQueryAnnotation(source: PsiElement): PsiMethod? {
        return source.findTopParentBy { method ->
            method as? PsiMethod ?: return@findTopParentBy false
            method.annotations.containsElements {
                it.hasQualifiedName(QUERY_FQN)
            }
        } as? PsiMethod
    }

    private fun findInjectedQuery(queryAnnotation: PsiAnnotation): PsiElement? {
        val languageManager = InjectedLanguageManager.getInstance(queryAnnotation.project)
        val specifiedValue =
            queryAnnotation.findAttribute("value") as? PsiNameValuePair ?: return null
        val literalExpression =
            specifiedValue.findAllChildrenOfType(PsiLiteralExpression::class.java)
                .getOrNull(0) ?: return null

        return languageManager.getInjectedPsiFiles(literalExpression)?.getOrNull(0)?.first
    }
}
