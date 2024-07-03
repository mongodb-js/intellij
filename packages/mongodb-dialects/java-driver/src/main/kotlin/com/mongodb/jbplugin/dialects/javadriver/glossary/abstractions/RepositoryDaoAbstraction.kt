package com.mongodb.jbplugin.dialects.javadriver.glossary.abstractions

import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTypesUtil
import com.mongodb.jbplugin.dialects.javadriver.glossary.Abstraction
import com.mongodb.jbplugin.dialects.javadriver.glossary.findContainingClass

object RepositoryDaoAbstraction : Abstraction {
    override fun isIn(psiElement: PsiElement): Boolean {
        val containingClass = psiElement.findContainingClass()
        val classNameLowerCase = containingClass.name?.lowercase() ?: return false

        val nameIsValid = classNameLowerCase.endsWith("repository") || classNameLowerCase.endsWith("dao")
        if (!nameIsValid) {
            return false
        }

        val javaFacade = JavaPsiFacade.getInstance(psiElement.project)
        val mdbClientClass =
            javaFacade.findClass(
                "com.mongodb.client.MongoClient",
                GlobalSearchScope.everythingScope(psiElement.project),
            )

        val mdbDatabaseClass =
            javaFacade.findClass(
                "com.mongodb.client.MongoDatabase",
                GlobalSearchScope.everythingScope(psiElement.project),
            )

        val mdbCollectionClass =
            javaFacade.findClass(
                "com.mongodb.client.MongoCollection",
                GlobalSearchScope.everythingScope(psiElement.project),
            )

        return containingClass.allFields.any {
            val typeClass = PsiTypesUtil.getPsiClass(it.type)
            typeClass == mdbClientClass ||
                typeClass == mdbDatabaseClass ||
                typeClass == mdbCollectionClass
        }
    }
}
