package com.mongodb.jbplugin.dialects.springquery

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiJavaFile
import com.mongodb.jbplugin.dialects.ConnectionContextExtractor
import com.mongodb.jbplugin.dialects.Dialect
import com.mongodb.jbplugin.dialects.DialectFormatter
import com.mongodb.jbplugin.dialects.DialectParser
import com.mongodb.jbplugin.dialects.javadriver.glossary.JavaDriverDialectFormatter
import com.mongodb.jbplugin.dialects.springcriteria.SpringCriteriaContextExtractor

const val QUERY_FQN = "org.springframework.data.mongodb.repository.Query"

object SpringAtQueryDialect : Dialect<PsiElement, Project> {
    override val parser: DialectParser<PsiElement>
        get() = SpringAtQueryDialectParser

    override val formatter: DialectFormatter
        get() = JavaDriverDialectFormatter

    override val connectionContextExtractor: ConnectionContextExtractor<Project>?
        get() = SpringCriteriaContextExtractor

    override fun isUsableForSource(source: PsiElement): Boolean {
        val psiFile = source.containingFile as? PsiJavaFile ?: return false
        val importStatements = psiFile.importList?.allImportStatements ?: emptyArray()
        return importStatements.any {
            return@any it.importReference?.canonicalText?.startsWith(
                QUERY_FQN
            ) ==
                true
        }
    }
}
