/**
 * Defines an interface for all abstractions that will be analysed for the Java
 * driver.
 */

package com.mongodb.jbplugin.dialects.javadriver.glossary

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
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
fun PsiElement.findContainingClass(): PsiClass = parentOfType<PsiClass>(withSelf = true)
 ?: childrenOfType<PsiClass>().first()
