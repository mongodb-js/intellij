package com.mongodb.jbplugin.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.psi.PsiElement
import com.mongodb.jbplugin.mql.Node

/**
 * This interface is for running inspections that only depend on the parsed query. Essentially we will implement
 * this interface whenever possible instead of using IntelliJ's bindings. We will use a bridge implementation that
 * will leverage caching.
 *
 * @see com.mongodb.jbplugin.inspections.AbstractMongoDbInspectionBridge for the actual implementation.
 */
interface MongoDbInspection {
    fun visitMongoDbQuery(
        dataSource: LocalDataSource?,
        problems: ProblemsHolder,
        query: Node<PsiElement>,
    )
}
