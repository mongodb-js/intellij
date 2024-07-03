/**
 * Defines an interface for all abstractions that will be analysed for the Java
 * driver.
 */

package com.mongodb.jbplugin.dialects.javadriver.glossary

import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTypesUtil
import com.intellij.psi.util.childrenOfType
import com.intellij.psi.util.parentOfType

/**
 * Represents an abstraction defined in the glossary document.
 */
interface Abstraction {
    fun isIn(psiElement: PsiElement): Boolean
}

/**
 * Helper extension function to get the containing class of any element.
 *
 * @return
 */
fun PsiElement.findContainingClass(): PsiClass =
    parentOfType<PsiClass>(withSelf = true)
        ?: childrenOfType<PsiClass>().first()

/**
 * Helper function to check if a type is a MongoDB Class
 *
 * @param project
 * @return
 */
fun PsiType.isMongoDbClass(project: Project): Boolean {
    val javaFacade = JavaPsiFacade.getInstance(project)
    val mdbClientClass =
        javaFacade.findClass(
            "com.mongodb.client.MongoClient",
            GlobalSearchScope.everythingScope(project),
        )

    val mdbDatabaseClass =
        javaFacade.findClass(
            "com.mongodb.client.MongoDatabase",
            GlobalSearchScope.everythingScope(project),
        )

    val mdbCollectionClass =
        javaFacade.findClass(
            "com.mongodb.client.MongoCollection",
            GlobalSearchScope.everythingScope(project),
        )

    val typeClass = PsiTypesUtil.getPsiClass(this)
    return typeClass == mdbClientClass ||
        typeClass == mdbDatabaseClass ||
        typeClass == mdbCollectionClass
}
