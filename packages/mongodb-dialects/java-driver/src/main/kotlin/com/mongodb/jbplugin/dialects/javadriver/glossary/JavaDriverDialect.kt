package com.mongodb.jbplugin.dialects.javadriver.glossary

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiJavaFile
import com.mongodb.jbplugin.dialects.ConnectionContextExtractor
import com.mongodb.jbplugin.dialects.Dialect
import com.mongodb.jbplugin.dialects.DialectFormatter
import com.mongodb.jbplugin.dialects.DialectParser

object JavaDriverDialect : Dialect<PsiElement, Project> {
    override fun isUsableForSource(source: PsiElement): Boolean {
        val psiFile = source.containingFile as? PsiJavaFile ?: return false
        val importStatements = psiFile.importList?.allImportStatements ?: emptyArray()
        return importStatements.any {
            return@any it.importReference?.canonicalText?.startsWith("com.mongodb") == true
        }
    }

    override val parser: DialectParser<PsiElement>
        get() = JavaDriverDialectParser

    override val formatter: DialectFormatter
        get() = JavaDriverDialectFormatter

    override val connectionContextExtractor: ConnectionContextExtractor<Project>?
        get() = null
}
