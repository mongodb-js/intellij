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
import com.mongodb.jbplugin.mql.BsonAny
import com.mongodb.jbplugin.mql.BsonAnyOf
import com.mongodb.jbplugin.mql.BsonArray
import com.mongodb.jbplugin.mql.BsonDouble
import com.mongodb.jbplugin.mql.BsonInt32
import com.mongodb.jbplugin.mql.BsonString
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.components.HasCollectionReference
import com.mongodb.jbplugin.mql.components.HasFieldReference
import com.mongodb.jbplugin.mql.components.HasFilter
import com.mongodb.jbplugin.mql.components.HasSourceDialect
import com.mongodb.jbplugin.mql.components.HasValueReference
import com.mongodb.jbplugin.mql.components.IsCommand
import com.mongodb.jbplugin.mql.components.Name
import com.mongodb.jbplugin.mql.components.Named

object SpringAtQueryDialectParser : DialectParser<PsiElement> {
    private val unknownCollectionReference = HasCollectionReference(
        HasCollectionReference.Unknown as HasCollectionReference.CollectionReference<PsiElement>
    )

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

        val sourceDialect = HasSourceDialect(HasSourceDialect.DialectName.SPRING_QUERY)
        val collection = resolveMethodCollection(source)

        val queryAnnotation = source.annotations.find {
            it.hasQualifiedName(QUERY_FQN)
        } ?: return Node(source, listOf(sourceDialect, collection))

        val operation = inferCommandFromQuery(source, queryAnnotation)
        val injectedQueryHost = findInjectedQuery(queryAnnotation)
        val injectedQuery =
            injectedQueryHost?.children?.firstOrNull()
                ?: return Node(source, listOf(sourceDialect, operation, collection))

        return Node(
            source,
            listOf(
                sourceDialect,
                operation,
                collection,
                HasFilter(recursivelyParseJsonFilter(injectedQuery, source))
            )
        )
    }

    private fun recursivelyParseJsonFilter(source: PsiElement, parent: PsiMethod): List<Node<PsiElement>> {
        return when (source.elementType?.toString()) {
            "ARRAY", "OBJECT" -> {
                return source.children.flatMap {
                    recursivelyParseJsonFilter(it, parent)
                }
            }
            "PROPERTY" -> {
                val fieldText = source.children[0].text
                val operator = Name.from(fieldText.substring(1))
                if (operator != Name.UNKNOWN &&
                    operator != Name.IN &&
                    operator != Name.NIN &&
                    source.children[1].elementType.toString() == "ARRAY"
                ) { // it's an operator with children
                    val opName = Named(Name.from(fieldText.substring(1)))
                    return listOf(
                        Node(
                            source,
                            listOf(
                                opName,
                                HasFilter(
                                    recursivelyParseJsonFilter(source.children[1], parent)
                                )
                            )
                        )
                    )
                } else if (operator != Name.UNKNOWN) { // it's an operator with a single value
                    val opName = Named(Name.from(fieldText.substring(1)))
                    return listOf(
                        Node(
                            source,
                            listOf(
                                opName,
                                resolveValueReference(source.children[1], parent)
                            )
                        )
                    )
                } else if (isImplicitAnd(source.children.getOrNull(1))) {
                    val fieldName = resolveToFieldNameReference(source.children[0])
                    val allFilters = recursivelyParseJsonFilter(source.children[1], parent).map {
                        it.copy(components = it.components + fieldName)
                    }
                    return listOf(
                        Node(
                            source,
                            listOf(
                                Named(Name.AND),
                                HasFilter(allFilters)
                            )
                        )
                    )
                } else {
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
            }
            else -> emptyList()
        }
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
            }
            "NUMBER_LITERAL" -> {
                val valueRefText = valueRef.text
                return if (valueRefText.contains('.')) {
                    HasValueReference(
                        HasValueReference.Constant(valueRef, valueRef.text.toDouble(), BsonDouble)
                    )
                } else {
                    HasValueReference(
                        HasValueReference.Constant(valueRef, valueRef.text.toInt(), BsonInt32)
                    )
                }
            }
            "STRING_LITERAL" -> return HasValueReference(
                HasValueReference.Constant(valueRef, valueRef.text.trim('\''), BsonString)
            )
            "OBJECT" -> {
                return HasValueReference(
                    HasValueReference.Constant(valueRef, null, BsonAny)
                )
            }
            "ARRAY" -> {
                val arrayValues = valueRef.children.map {
                    resolveValueReference(it, parent)
                }.mapNotNull {
                    when (val ref = it.reference) {
                        is HasValueReference.Runtime -> ref.type
                        is HasValueReference.Constant -> ref.type
                        else -> null
                    }
                }.toSet()

                return HasValueReference(
                    HasValueReference.Runtime(valueRef, BsonArray(BsonAnyOf(arrayValues)))
                )
            }
            else -> {
                return HasValueReference(
                    HasValueReference.Unknown as HasValueReference.ValueReference<PsiElement>
                )
            }
        }
    }

    private fun resolveMethodCollection(method: PsiMethod): HasCollectionReference<PsiElement> {
        val fqnSpringRepositoryInterface = JavaPsiFacade.getInstance(method.project)
            .findClass(
                "org.springframework.data.repository.Repository",
                GlobalSearchScope.allScope(method.project)
            )
            ?: return unknownCollectionReference

        val methodClass = method.containingClass ?: return unknownCollectionReference

        val repositoryInterface = methodClass.extendsList?.referencedTypes?.find { child ->
            val psiClass = child.resolve() ?: return@find false

            psiClass.isInterface &&
                (
                    psiClass.isInheritorDeep(fqnSpringRepositoryInterface, null) ||
                        psiClass == fqnSpringRepositoryInterface
                    )
        } ?: return unknownCollectionReference

        val typeClass =
            repositoryInterface.parameters.getOrNull(0) ?: return unknownCollectionReference

        val typeClassPsi = PsiTypesUtil.getPsiClass(typeClass) ?: return unknownCollectionReference

        val extractedCollection =
            ModelCollectionExtractor.fromPsiClass(typeClassPsi) ?: return unknownCollectionReference

        return HasCollectionReference(
            HasCollectionReference.OnlyCollection(typeClassPsi, extractedCollection)
        )
    }

    private fun isImplicitAnd(valueRef: PsiElement?): Boolean {
        if (valueRef == null) {
            return false
        }

        val hasProps = valueRef.children.all {
            val propName = it.children[0].text
            propName.startsWith('$')
        }

        val isObj = valueRef.elementType.toString() == "OBJECT"

        return isObj && hasProps
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

    private fun inferCommandFromQuery(method: PsiMethod, queryAnnotation: PsiAnnotation): IsCommand {
        return IsCommand(
            when {
                queryAnnotation.findAttributeValue("count")?.text == "true" ->
                    IsCommand.CommandType.COUNT_DOCUMENTS
                queryAnnotation.findAttributeValue("exists")?.text == "true" ->
                    IsCommand.CommandType.FIND_ONE
                queryAnnotation.findAttributeValue("delete")?.text == "true" ->
                    IsCommand.CommandType.DELETE_MANY
                doesMethodReturnSingleObject(method) ->
                    IsCommand.CommandType.FIND_ONE
                else -> IsCommand.CommandType.FIND_MANY
            }
        )
    }

    private fun doesMethodReturnSingleObject(method: PsiMethod): Boolean {
        val javaFacade = JavaPsiFacade.getInstance(method.project)
        val iterableClass =
            javaFacade.findClass(
                "java.lang.Iterable",
                GlobalSearchScope.everythingScope(method.project)
            )
                ?: return true // the jdk is not available

        val baseStreamClass =
            javaFacade.findClass(
                "java.util.stream.BaseStream",
                GlobalSearchScope.everythingScope(method.project)
            )
                ?: return true // the jdk is not available

        val returnType = method.returnType ?: return true // if we don't know the type, assume it's a single object
        val returnPsiClass = PsiTypesUtil.getPsiClass(returnType) ?: return true

        if (returnPsiClass == iterableClass || returnPsiClass == baseStreamClass) {
            return false
        }

        if (returnPsiClass.isInheritorDeep(iterableClass, null)) {
            return false
        }
        if (returnPsiClass.isInheritorDeep(baseStreamClass, null)) {
            return false
        }

        return true
    }
}
