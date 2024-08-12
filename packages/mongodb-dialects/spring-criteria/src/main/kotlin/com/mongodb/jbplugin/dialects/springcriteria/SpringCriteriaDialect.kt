package com.mongodb.jbplugin.dialects.springcriteria

import com.intellij.psi.PsiElement
import com.mongodb.jbplugin.dialects.Dialect
import com.mongodb.jbplugin.dialects.DialectFormatter
import com.mongodb.jbplugin.dialects.DialectParser
import com.mongodb.jbplugin.dialects.javadriver.glossary.JavaDriverDialectFormatter

object SpringCriteriaDialect : Dialect<PsiElement> {
    override val parser: DialectParser<PsiElement>
        get() = TODO("Not yet implemented")

    override val formatter: DialectFormatter
        get() = JavaDriverDialectFormatter
}