package com.mongodb.jbplugin.inspections.impl

import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.mongodb.jbplugin.accessadapter.ExplainPlan
import com.mongodb.jbplugin.accessadapter.slice.ExplainQuery
import com.mongodb.jbplugin.dialects.springcriteria.SpringCriteriaDialect
import com.mongodb.jbplugin.fixtures.*
import com.mongodb.jbplugin.mql.*
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.eq

// Suppressing
// - LONG_LINE because the complaint is about the templated error description which needs to be in the same line for the
// match to happen correctly
// - TOO_LONG_FUNCTION because it is better to keep test logic within the tests and not make them "too smart" otherwise
// reading through them becomes a task in its own
@Suppress("LONG_LINE", "TOO_LONG_FUNCTION")
@CodeInsightTest
class SpringCriteriaIndexCheckLinterInspectionTest {
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
        template.find(
            query(
            <warning descr="To optimize query performance in MongoDB, consider leveraging indexes on frequently queried fields. Indexes store a subset of the collection's data, enabling efficient document retrieval and minimizing the number of documents examined.

By carefully designing indexes based on your application's query patterns you can significantly enhance query speed, reduce resource consumption and improve scalability. Especially for large datasets and complex queries, indexes are crucial for maintaining optimal performance as your data volume grows.">where("released")</warning>
            // TODO: (INTELLIJ-62) The Java SDK is not available in the test class path which is why there is
            // an error in the .is block and hence expected.
            .is<error descr="'is(java.lang.Object)' in 'org.springframework.data.mongodb.core.query.Criteria' cannot be applied to '(boolean)'">(true)</error>),
            Book.class
        );
    }
}
        """,
    )
    fun `shows an inspection when the query is a collscan`(
        psiFile: PsiFile,
        fixture: CodeInsightTestFixture,
    ) {
        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDialect(SpringCriteriaDialect)

        `when`(readModelProvider.slice(eq(dataSource), any<ExplainQuery.Slice<Any>>())).thenReturn(
            ExplainQuery(ExplainPlan.CollectionScan)
        )

        fixture.enableInspections(IndexCheckInspectionBridge::class.java)
        fixture.testHighlighting()
    }
}