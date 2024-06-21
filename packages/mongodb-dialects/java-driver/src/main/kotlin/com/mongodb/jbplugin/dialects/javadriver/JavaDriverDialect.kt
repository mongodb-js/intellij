package com.mongodb.jbplugin.dialects.javadriver

import com.intellij.psi.PsiElement
import com.mongodb.jbplugin.dialects.Dialect

object JavaDriverDialect : Dialect<PsiElement> {
    override val parser = JavaDriverDialectParser
}
