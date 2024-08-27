package com.mongodb.jbplugin.dialects.springcriteria

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.mongodb.jbplugin.dialects.ConnectionContext
import com.mongodb.jbplugin.dialects.ConnectionContextExtractor
import com.mongodb.jbplugin.dialects.ConnectionMetadataRequirement
import java.util.*

object SpringCriteriaContextExtractor : ConnectionContextExtractor<Project> {

    override fun requirements(): Set<ConnectionMetadataRequirement> {
        return setOf(ConnectionMetadataRequirement.DATABASE)
    }

    override fun hasContextToGather(contentRoot: Project): Boolean {
        val rootManager = ProjectRootManager.getInstance(contentRoot)
        return rootManager.contentSourceRoots.any {
            it.findChild("application.properties") != null
        }
    }

    override fun gatherContext(contentRoot: Project): ConnectionContext {
        val database = extractDatabase(contentRoot)
        return ConnectionContext(
            database = database
        )
    }

    private fun extractDatabase(project: Project): String? {
        val rootManager = ProjectRootManager.getInstance(project)
        val propertiesFile = rootManager.contentSourceRoots
            .firstNotNullOfOrNull {
                it.findChild("application.properties")
            } ?: return null

        val properties = Properties()
        properties.load(propertiesFile.inputStream)
        return tryResolveConstantString(properties["spring.data.mongodb.database"])
    }

    private fun tryResolveConstantString(value: Any?): String? {
        if (value !is String) return null

        if (value.matches(Regex(".*[$#]\\{.*}.*"))) {
            return null // uses Spring Expressions
        }

        return value
    }
}