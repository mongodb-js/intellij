package com.mongodb.jbplugin.dialects.javadriver.glossary

import com.intellij.psi.PsiElement
import com.mongodb.jbplugin.dialects.Dialect
import com.mongodb.jbplugin.dialects.DialectParser

object JavaDriverDialect : Dialect<PsiElement> {
    override val parser: DialectParser<PsiElement>
        get() = JavaDriverDialectParser
}
