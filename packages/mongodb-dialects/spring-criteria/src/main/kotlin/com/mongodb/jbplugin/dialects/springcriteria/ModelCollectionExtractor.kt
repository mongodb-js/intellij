package com.mongodb.jbplugin.dialects.springcriteria

import com.intellij.psi.PsiClass
import com.mongodb.jbplugin.dialects.javadriver.glossary.tryToResolveAsConstantString

private const val AT_DOCUMENT_ANNOTATION = "org.springframework.data.mongodb.core.mapping.Document"

object ModelCollectionExtractor {
    fun fromPsiClass(clazz: PsiClass): String? {
        val annotation = clazz.getAnnotation(AT_DOCUMENT_ANNOTATION)
        annotation?.let {
            val collectionName =
                annotation.findAttributeValue("value")?.tryToResolveAsConstantString()
                    .takeIf { !it.isNullOrBlank() }
                    ?: annotation.findAttributeValue("collection")?.tryToResolveAsConstantString()
                        .takeIf { !it.isNullOrBlank() }

            collectionName?.let {
                return collectionName
            }

            return toCollectionName(clazz)
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
