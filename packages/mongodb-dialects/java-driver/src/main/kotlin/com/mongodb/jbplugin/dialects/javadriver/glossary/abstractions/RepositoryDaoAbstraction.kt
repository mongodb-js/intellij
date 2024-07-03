package com.mongodb.jbplugin.dialects.javadriver.glossary.abstractions

import com.intellij.psi.PsiElement
import com.mongodb.jbplugin.dialects.javadriver.glossary.Abstraction
import com.mongodb.jbplugin.dialects.javadriver.glossary.findContainingClass
import com.mongodb.jbplugin.dialects.javadriver.glossary.isMongoDbClass

object RepositoryDaoAbstraction : Abstraction {
    override fun isIn(psiElement: PsiElement): Boolean {
        val containingClass = psiElement.findContainingClass()
        val classNameLowerCase = containingClass.name?.lowercase() ?: return false

        val nameIsValid = classNameLowerCase.endsWith("repository") || classNameLowerCase.endsWith("dao")
        if (!nameIsValid) {
            return false
        }

        return containingClass.allFields.any { it.type.isMongoDbClass(psiElement.project) }
    }
}
