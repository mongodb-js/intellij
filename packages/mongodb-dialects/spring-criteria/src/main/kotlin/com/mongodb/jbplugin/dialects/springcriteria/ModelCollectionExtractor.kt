package com.mongodb.jbplugin.dialects.springcriteria

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.mongodb.jbplugin.dialects.javadriver.glossary.tryToResolveAsConstantString

private const val AT_DOCUMENT_ANNOTATION = "org.springframework.data.mongodb.core.mapping.Document"

object ModelCollectionExtractor {
    fun fromPsiClass(clazz: PsiClass): Pair<String, PsiElement?>? {
        val annotation = clazz.getAnnotation(AT_DOCUMENT_ANNOTATION)
        annotation?.let {
            val valueAttr = annotation.findAttributeValue("value")
            val collectionAttr = annotation.findAttributeValue("collection")

            val collectionName = valueAttr?.tryToResolveAsConstantString()
                .takeIf { !it.isNullOrBlank() }
                ?: collectionAttr?.tryToResolveAsConstantString()
                    .takeIf { !it.isNullOrBlank() }

            collectionName?.let {
                return (it to listOfNotNull(valueAttr, collectionAttr).firstOrNull())
            }

            return (toCollectionName(clazz) to listOfNotNull(valueAttr, collectionAttr).firstOrNull())
        }

        // not in the current class, check parent classes and interfaces
        return (clazz.interfaces + clazz.superClass)
            .filterNotNull()
            .firstNotNullOfOrNull { fromPsiClass(it) }
    }

    /** According to the documentation, it's the class name but with the first letter lower cased:
     * https://docs.spring.io/spring-data/mongodb/docs/3.0.1.RELEASE/reference/html/
     */
    private fun toCollectionName(clazz: PsiClass): String {
        val className = clazz.name!!
        return className[0].lowercaseChar() + className.substring(1)
    }
}