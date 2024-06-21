package com.mongodb.jbplugin.inspections

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Key
import com.intellij.psi.JavaElementVisitor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.mongodb.jbplugin.dialects.Dialect
import com.mongodb.jbplugin.dialects.javadriver.JavaDriverDialect
import com.mongodb.jbplugin.mql.ast.Node

abstract class AbstractJavaMongoDbInspection(
    private val dialect: Dialect<PsiElement>,
) : AbstractBaseJavaLocalInspectionTool() {
    private val queryKey: Key<CachedValue<Node<PsiElement>>> =
        Key.create(
            "QueryForDialect" + dialect.javaClass.name,
        )

    abstract fun visitMongoDbQuery(
        problems: ProblemsHolder,
        query: Node<PsiElement>,
    )

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
        session: LocalInspectionToolSession,
    ): PsiElementVisitor {
        return object : JavaElementVisitor() {
            override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
                ApplicationManager.getApplication().runReadAction {
                    var cachedValue: CachedValue<Node<PsiElement>>? = null
                    if (dialect.parser.canParse(expression)) {
                        val attachment = dialect.parser.attachment(expression)
                        if (attachment.getUserData(queryKey) == null) {
                            val parsedAst =
                                CachedValuesManager.getManager(attachment.project).createCachedValue {
                                    val parsedAst = JavaDriverDialect.parser.parse(expression)
                                    CachedValueProvider.Result.create(parsedAst, attachment)
                                }

                            attachment.putUserData(queryKey, parsedAst)
                            cachedValue = parsedAst
                        } else {
                            cachedValue = attachment.getUserData(queryKey)!!
                        }
                    }

                    if (cachedValue != null) {
                        visitMongoDbQuery(holder, cachedValue.value)
                    }
                }
            }
        }
    }
}
