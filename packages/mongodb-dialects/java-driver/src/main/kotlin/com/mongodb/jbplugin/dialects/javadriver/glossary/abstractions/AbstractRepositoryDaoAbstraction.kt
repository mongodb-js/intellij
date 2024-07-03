package com.mongodb.jbplugin.dialects.javadriver.glossary.abstractions

import com.intellij.psi.PsiElement
import com.mongodb.jbplugin.dialects.javadriver.glossary.Abstraction
import com.mongodb.jbplugin.dialects.javadriver.glossary.findContainingClass

object AbstractRepositoryDaoAbstraction : Abstraction {
    override fun isIn(psiElement: PsiElement): Boolean {
        val containingClass = psiElement.findContainingClass()
        containingClass.superClass ?: return false

        return RepositoryDaoAbstraction.isIn(containingClass.superClass?.originalElement!!)
    }
}
