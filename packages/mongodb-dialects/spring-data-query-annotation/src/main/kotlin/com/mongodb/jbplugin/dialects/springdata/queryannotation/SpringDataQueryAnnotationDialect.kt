package com.mongodb.jbplugin.dialects.springdata.queryannotation

import com.intellij.psi.PsiElement
import com.mongodb.jbplugin.dialects.Dialect

object SpringDataQueryAnnotationDialect : Dialect<PsiElement> {
    override val parser = SpringDataQueryAnnotationDialectParser
}
