package com.mongodb.jbplugin.dialects.springcriteria

import com.intellij.openapi.project.Project
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.mongodb.jbplugin.dialects.ConnectionContext
import com.mongodb.jbplugin.dialects.ConnectionContextExtractor
import com.mongodb.jbplugin.dialects.ConnectionContextRequirement
import java.util.*

object SpringCriteriaContextExtractor : ConnectionContextExtractor<Project> {
    override fun requirements(): Set<ConnectionContextRequirement> = setOf(ConnectionContextRequirement.DATABASE)

    override fun gatherContext(contentRoot: Project): ConnectionContext {
        val database = extractDatabase(contentRoot)
        return ConnectionContext(
            database = database
        )
    }

    private fun extractDatabase(project: Project): String? {
        val allVirtualFiles = FilenameIndex.getVirtualFilesByName(
            "application.properties",
            GlobalSearchScope.projectScope(project)
        )

        if (allVirtualFiles.isEmpty()) {
            return null
        }

        val propertiesFile = allVirtualFiles.first()
        val properties = Properties()
        properties.load(propertiesFile.inputStream)
        return tryResolveConstantString(properties["spring.data.mongodb.database"])
    }

    private fun tryResolveConstantString(value: Any?): String? {
        if (value !is String) {
            return null
        }

        if (value.matches(Regex(".*[$#]\\{.*}.*"))) {
            return null // uses Spring Expressions
        }

        return value
    }
}