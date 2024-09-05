package com.mongodb.jbplugin.inspections.impl

import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.mongodb.jbplugin.accessadapter.ExplainPlan
import com.mongodb.jbplugin.accessadapter.slice.ExplainQuery
import com.mongodb.jbplugin.dialects.javadriver.glossary.JavaDriverDialect
import com.mongodb.jbplugin.fixtures.CodeInsightTest
import com.mongodb.jbplugin.fixtures.ParsingTest
import com.mongodb.jbplugin.fixtures.setupConnection
import com.mongodb.jbplugin.fixtures.specifyDialect
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.eq

@CodeInsightTest
@Suppress("TOO_LONG_FUNCTION", "LONG_LINE")
class JavaDriverIndexCheckLinterInspectionTest {
    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.bson.types.ObjectId;
import static com.mongodb.client.model.Filters.*;

public class Repository {
    private final MongoClient client;

    public Repository(MongoClient client) {
        this.client = client;
    }

    public FindIterable<Document> exampleFind() {
        return <warning descr="To optimize query performance in MongoDB, consider leveraging indexes on frequently queried fields. Indexes store a subset of the collection's data, enabling efficient document retrieval and minimizing the number of documents examined.

By carefully designing indexes based on your application's query patterns you can significantly enhance query speed, reduce resource consumption and improve scalability. Especially for large datasets and complex queries, indexes are crucial for maintaining optimal performance as your data volume grows.">client.getDatabase("myDatabase")
                .getCollection("myCollection")
                .find()</warning>;
    }
}
        """,
    )
    fun `shows an inspection when the query is a collscan`(
        psiFile: PsiFile,
        fixture: CodeInsightTestFixture,
    ) {
        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDialect(JavaDriverDialect)

        `when`(readModelProvider.slice(eq(dataSource), any<ExplainQuery.Slice<Any>>())).thenReturn(
            ExplainQuery(ExplainPlan.CollectionScan)
        )

        fixture.enableInspections(IndexCheckInspectionBridge::class.java)
        fixture.testHighlighting()
    }
}
