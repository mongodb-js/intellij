package com.mongodb.jbplugin.codeActions.impl

import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.mongodb.jbplugin.dialects.springcriteria.SpringCriteriaDialect
import com.mongodb.jbplugin.fixtures.CodeInsightTest
import com.mongodb.jbplugin.fixtures.ParsingTest
import com.mongodb.jbplugin.fixtures.setupConnection
import com.mongodb.jbplugin.fixtures.specifyDialect
import com.mongodb.jbplugin.i18n.CodeActionsMessages
import com.mongodb.jbplugin.i18n.Icons
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

@CodeInsightTest
@Suppress("TOO_LONG_FUNCTION", "LONG_LINE")
class SpringCriteriaRunQueryCodeActionTest {
    @ParsingTest(
        fileName = "Repository.java",
        value = """
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.mapping.Document;

import static org.springframework.data.mongodb.core.query.Query.query;
import static org.springframework.data.mongodb.core.query.Criteria.where;

@Document
record Book() {}

class BookRepository {
    private final MongoTemplate template;

    public BookRepository(MongoTemplate template) {
        this.template = template;
    }

    public void allReleasedBooks() {
        template.find(query(where("released").is(true)), Book.class);
    }
}
        """,
    )
    fun `does not show a gutter icon if not connected`(
        psiFile: PsiFile,
        fixture: CodeInsightTestFixture,
    ) {
        fixture.specifyDialect(SpringCriteriaDialect)
        val gutters = fixture.findAllGutters()
        assertTrue(gutters.isEmpty())
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.mapping.Document;

import static org.springframework.data.mongodb.core.query.Query.query;
import static org.springframework.data.mongodb.core.query.Criteria.where;

@Document
record Book() {}

class BookRepository {
    private final MongoTemplate template;

    public BookRepository(MongoTemplate template) {
        this.template = template;
    }

    public void allReleasedBooks() {
        template.find(query(where("released").is(true)), Book.class);
    }
}
        """,
    )
    fun `does show a gutter icon if connected`(
        psiFile: PsiFile,
        fixture: CodeInsightTestFixture,
    ) {
        fixture.setupConnection()
        fixture.specifyDialect(SpringCriteriaDialect)

        val gutters = fixture.findAllGutters()
        assertEquals(1, gutters.size)

        val gutter = gutters.first()
        assertEquals(Icons.runQueryGutter, gutter.icon)
        assertEquals(CodeActionsMessages.message("code.action.run.query"), gutter.tooltipText)
    }
}