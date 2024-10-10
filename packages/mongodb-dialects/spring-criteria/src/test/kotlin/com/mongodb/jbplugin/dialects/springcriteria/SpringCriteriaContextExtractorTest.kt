package com.mongodb.jbplugin.dialects.springcriteria

import com.intellij.openapi.project.Project
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

@IntegrationTest
class SpringCriteriaContextExtractorTest {
    @Test
    @AdditionalFile(
        fileName = "application.properties",
        value = """
            spring.data.mongodb.database=myDatabase
        """
    )
    fun `extracts from a properties file if exist and is a constant string`(
        project: Project
    ) {
        val context = SpringCriteriaContextExtractor.gatherContext(project)
        assertEquals("myDatabase", context.database)
    }

    @Test
    @AdditionalFile(
        fileName = "application.properties",
        value = """
            spring.data.mongodb.database=$\{abc}
        """
    )
    fun `does not extract from a properties file if it depends on spring expressions`(
        project: Project
    ) {
        val context = SpringCriteriaContextExtractor.gatherContext(project)
        assertNull(context.database)
    }

    @Test
    @AdditionalFile(
        fileName = "application.properties",
        value = """
            spring.data.mongodb.database=#{abc}
        """
    )
    fun `does not extract from a properties file if it depends on spring expressions on env var`(
        project: Project
    ) {
        val context = SpringCriteriaContextExtractor.gatherContext(project)
        assertNull(context.database)
    }

    @Test
    @AdditionalFile(
        fileName = "application.yaml",
        value = """
            spring:
                data:
                    mongodb:
                        database: myDatabase
        """
    )
    fun `does extract from the application yaml in a nested structure`(
        project: Project
    ) {
        val context = SpringCriteriaContextExtractor.gatherContext(project)
        assertEquals("myDatabase", context.database)
    }

    @Test
    @AdditionalFile(
        fileName = "application.yml",
        value = """
            spring.data.mongodb.database: myDatabase
        """
    )
    fun `does extract from the application yaml in an inline structure`(
        project: Project
    ) {
        val context = SpringCriteriaContextExtractor.gatherContext(project)
        assertEquals("myDatabase", context.database)
    }
}
