package com.mongodb.jbplugin.dialects.springcriteria

import com.intellij.psi.PsiFile
import com.mongodb.jbplugin.dialects.javadriver.glossary.JavaDriverDialectFormatter
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

@IntegrationTest
class SpringCriteriaDialectTest {
    @ParsingTest(
        fileName = "MyRepository.java",
        value = """
import org.springframework.data.mongodb.core.MongoTemplate;
        """
    )
    fun `detects includes to spring criteria`(file: PsiFile) {
        assertTrue(
            SpringCriteriaDialect.isUsableForSource(file)
        )
    }

    @ParsingTest(
        fileName = "MyRepository.java",
        value = """
import org.sprungframework.dOTA.mangodb.kernel.MangoTemplate;
        """
    )
    fun `ignores other includes`(file: PsiFile) {
        assertFalse(
            SpringCriteriaDialect.isUsableForSource(file)
        )
    }

    @Test
    fun `uses it's own custom parser`() {
        assertEquals(
            SpringCriteriaDialectParser,
            SpringCriteriaDialect.parser
        )
    }

    @Test
    fun `uses the java base formatter`() {
        assertEquals(
            JavaDriverDialectFormatter,
            SpringCriteriaDialect.formatter
        )
    }

    @Test
    fun `uses a custom connection context extractor`() {
        assertEquals(
            SpringCriteriaContextExtractor,
            SpringCriteriaDialect.connectionContextExtractor
        )
    }
}
