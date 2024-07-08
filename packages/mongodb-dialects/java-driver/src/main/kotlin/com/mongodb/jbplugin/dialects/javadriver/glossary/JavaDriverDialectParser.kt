package com.mongodb.jbplugin.dialects.javadriver.glossary

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.util.PsiTreeUtil
import com.mongodb.jbplugin.dialects.DialectParser
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.components.HasCollectionReference

object JavaDriverDialectParser : DialectParser<PsiElement> {
    override fun canParse(source: PsiElement): Boolean =
        (source as? PsiMethodCallExpression)?.findMongoDbClassReference(source.project) != null

    override fun attachment(source: PsiElement): PsiElement =
        (source as PsiMethodCallExpression).findMongoDbClassReference(source.project)!!

    override fun parse(source: PsiElement): Node<PsiElement> {
        val owningMethod =
            PsiTreeUtil.getParentOfType(source, PsiMethod::class.java)
                ?: return Node(source, emptyList())
        val namespace = NamespaceExtractor.extractNamespace(owningMethod)

        return Node(
            source,
            listOf(
                namespace?.let {
                    HasCollectionReference(HasCollectionReference.Known(namespace))
                } ?: HasCollectionReference(HasCollectionReference.Unknown),
            ),
        )
    }
}
