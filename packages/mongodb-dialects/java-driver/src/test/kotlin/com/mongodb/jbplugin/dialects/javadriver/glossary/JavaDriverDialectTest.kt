package com.mongodb.jbplugin.dialects.javadriver.glossary

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class JavaDriverDialectTest {
    @Test
    fun `returns the correct parser`() {
        assertEquals(JavaDriverDialectParser, JavaDriverDialect.parser)
    }

    @Test
    fun `returns the correct formatter`() {
        assertEquals(JavaDriverDialectFormatter, JavaDriverDialect.formatter)
    }
}
