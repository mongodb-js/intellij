package com.mongodb.jbplugin.dialects.javadriver.glossary.abstractions

import com.intellij.psi.PsiElement
import com.mongodb.jbplugin.dialects.javadriver.glossary.Abstraction
import com.mongodb.jbplugin.dialects.javadriver.glossary.findContainingClass

object ConstructorInjectionAbstraction : Abstraction {
    override fun isIn(psiElement: PsiElement): Boolean {
        val containingClass = psiElement.findContainingClass()
        if (!AbstractRepositoryDaoAbstraction.isIn(containingClass)) {
            return false
        }

        return containingClass.constructors.any {
            it.text.contains("super(")
        }
    }
}
