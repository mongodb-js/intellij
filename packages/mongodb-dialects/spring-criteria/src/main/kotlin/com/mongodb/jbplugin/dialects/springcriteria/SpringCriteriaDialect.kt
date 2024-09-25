package com.mongodb.jbplugin.dialects.springcriteria

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiJavaFile
import com.mongodb.jbplugin.dialects.ConnectionContextExtractor
import com.mongodb.jbplugin.dialects.Dialect
import com.mongodb.jbplugin.dialects.DialectFormatter
import com.mongodb.jbplugin.dialects.DialectParser
import com.mongodb.jbplugin.dialects.javadriver.glossary.JavaDriverDialectFormatter

object SpringCriteriaDialect : Dialect<PsiElement, Project> {
    override val parser: DialectParser<PsiElement>
        get() = SpringCriteriaDialectParser

    override val formatter: DialectFormatter
        get() = JavaDriverDialectFormatter

    override val connectionContextExtractor: ConnectionContextExtractor<Project>
        get() = SpringCriteriaContextExtractor

    override fun isUsableForSource(source: PsiElement): Boolean {
        val psiFile = source.containingFile as? PsiJavaFile ?: return false
        val importStatements = psiFile.importList?.allImportStatements ?: emptyArray()
        return importStatements.any {
            return@any it.importReference?.canonicalText?.startsWith(
                "org.springframework.data.mongodb"
            ) ==
                true
        }
    }
}
