package com.mongodb.jbplugin.autocomplete

import com.intellij.database.util.common.containsElements
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.mongodb.jbplugin.accessadapter.slice.GetCollectionSchema
import com.mongodb.jbplugin.accessadapter.slice.ListCollections
import com.mongodb.jbplugin.dialects.springcriteria.SpringCriteriaDialect
import com.mongodb.jbplugin.fixtures.*
import com.mongodb.jbplugin.mql.BsonObject
import com.mongodb.jbplugin.mql.BsonString
import com.mongodb.jbplugin.mql.CollectionSchema
import com.mongodb.jbplugin.mql.Namespace
import org.junit.jupiter.api.Assertions.assertTrue
import org.mockito.Mockito.`when`
import org.mockito.kotlin.eq

@Suppress("TOO_LONG_FUNCTION")
@CodeInsightTest
class SpringCriteriaCompletionContributorTest {
    @ParsingTest(
        fileName = "Repository.java",
        value = """
import org.springframework.data.mongodb.core.mapping.Document;
@Document("<caret>")
record Book() {}
        """,
    )
    fun `should autocomplete collections from the current connection`(
        project: Project,
        psiFile: PsiFile,
        fixture: CodeInsightTestFixture,
    ) {
        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDatabase("myDatabase")
        fixture.specifyDialect(SpringCriteriaDialect)

        `when`(readModelProvider.slice(eq(dataSource), eq(ListCollections.Slice("myDatabase")))).thenReturn(
            ListCollections(
                listOf(
                    ListCollections.Collection("myCollection", "collection"),
                    ListCollections.Collection("anotherCollection", "collection"),
                ),
            ),
        )

        val elements = fixture.completeBasic()

        assertTrue(
            elements.containsElements {
                it.lookupString == "myCollection"
            },
        )

        assertTrue(
            elements.containsElements {
                it.lookupString == "anotherCollection"
            },
        )
    }

    @Suppress("TOO_LONG_FUNCTION")
    @ParsingTest(
        fileName = "Repository.java",
        value = """
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.query.Criteria;

import java.util.List;

import static org.springframework.data.mongodb.core.query.Criteria.where;

@Document
record Book() {}

class Repository {
    private final MongoTemplate template;
    
    public Repository(MongoTemplate template) {
        this.template = template;
    }
    
    public List<Book> allReleasedBooks() {
        return template.query(Book.class).matching(where("<caret>"
    }
}
        """,
    )
    fun `should autocomplete fields from the current namespace`(
        project: Project,
        psiFile: PsiFile,
        fixture: CodeInsightTestFixture,
    ) {
        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDatabase("myDatabase")
        fixture.specifyDialect(SpringCriteriaDialect)

        val namespace = Namespace("myDatabase", "book")

        `when`(readModelProvider.slice(eq(dataSource), eq(GetCollectionSchema.Slice(namespace)))).thenReturn(
            GetCollectionSchema(
                CollectionSchema(
                    namespace,
                    BsonObject(
                        mapOf(
                            "myField" to BsonString,
                            "myField2" to BsonString,
                        ),
                    ),
                ),
            ),
        )

        val elements = fixture.completeBasic()

        assertTrue(
            elements.containsElements {
                it.lookupString == "myField"
            },
        )

        assertTrue(
            elements.containsElements {
                it.lookupString == "myField2"
            },
        )
    }
}