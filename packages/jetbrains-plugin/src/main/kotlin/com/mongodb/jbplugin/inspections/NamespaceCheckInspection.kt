/**
 * Represents an inspection. This is a placeholder example and will be removed.
 */

@file:Suppress("FILE_NAME_MATCH_CLASS")

package com.mongodb.jbplugin.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.mongodb.jbplugin.dialects.javadriver.glossary.JavaDriverDialect
import com.mongodb.jbplugin.mql.Node

/** The inspection implementation. **/
object NamespaceCheckInspection : MongoDbInspection {
    override fun visitMongoDbQuery(
        problems: ProblemsHolder,
        query: Node<PsiElement>,
    ) {
    }
}

/** The connection between a driver and the inspection. **/
class JavaDriverNamespaceCheckInspectionBridge :
    AbstractMongoDbInspectionBridge(
        JavaDriverDialect,
        NamespaceCheckInspection,
    )
