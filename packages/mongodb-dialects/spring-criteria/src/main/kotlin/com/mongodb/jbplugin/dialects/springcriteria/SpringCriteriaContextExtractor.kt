package com.mongodb.jbplugin.dialects.springcriteria

import com.intellij.openapi.project.Project
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.containers.tail
import com.mongodb.jbplugin.dialects.ConnectionContext
import com.mongodb.jbplugin.dialects.ConnectionContextExtractor
import com.mongodb.jbplugin.dialects.ConnectionContextRequirement
import org.yaml.snakeyaml.Yaml
import java.util.*

object SpringCriteriaContextExtractor : ConnectionContextExtractor<Project> {
    override fun requirements(): Set<ConnectionContextRequirement> = setOf(
        ConnectionContextRequirement.DATABASE
    )

    override fun gatherContext(contentRoot: Project): ConnectionContext {
        val database = extractDatabaseFromProperties(contentRoot)
            ?: extractDatabaseFromYaml(contentRoot)

        return ConnectionContext(
            database = database
        )
    }

    private fun extractDatabaseFromYaml(project: Project): String? {
        val allVirtualFiles = FilenameIndex.getVirtualFilesByName(
            "application.yaml",
            GlobalSearchScope.projectScope(project)
        ) + FilenameIndex.getVirtualFilesByName(
            "application.yml",
            GlobalSearchScope.projectScope(project)
        ) // YAML can use both extensions, and both are allowed in Spring

        if (allVirtualFiles.isEmpty()) {
            return null
        }
        val yamlFile = allVirtualFiles.first()
        val yaml = Yaml()
        val config = yaml.load<Map<String, Any>>(yamlFile.inputStream)

        return readNestedPath(config, "spring.data.mongodb.database".split("."))
    }

    private fun extractDatabaseFromProperties(project: Project): String? {
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

    private fun readNestedPath(map: Map<String, Any>, path: List<String>): String? {
        val value = map.getOrDefault(path.joinToString(separator = "."), null)
        if (value is String) {
            return value
        }

        val nextPath = path.tail()
        if (nextPath.isEmpty()) {
            return null
        }

        val nextMap = map.getOrDefault(path.first(), null)
        if (nextMap != null && nextMap is Map<*, *>) {
            return readNestedPath(nextMap as Map<String, Any>, nextPath)
        }

        return null
    }
}
