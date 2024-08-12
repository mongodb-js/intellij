package com.mongodb.jbplugin.dialects.springcriteria

import com.intellij.psi.PsiElement
import com.mongodb.jbplugin.dialects.DialectParser
import com.mongodb.jbplugin.mql.Node

object SpringCriteriaDialectParser : DialectParser<PsiElement> {
    override fun isCandidateForQuery(source: PsiElement): Boolean = false

    override fun attachment(source: PsiElement): PsiElement = source

    override fun parse(source: PsiElement): Node<PsiElement> = Node(source, emptyList())
}