package com.mongodb.jbplugin.inspections.impl

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.mongodb.jbplugin.accessadapter.slice.ListCollections
import com.mongodb.jbplugin.accessadapter.slice.ListDatabases
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
class JavaDriverNamespaceCheckLinterInspectionTest {
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
        return <warning descr="Cannot resolve \"myDatabase\" database reference in the connected data source.">client.getDatabase("myDatabase")</warning>
                .getCollection("myCollection")
                .find(eq("nonExistingField", "123"));
    }
}
        """,
    )
    fun `shows an inspection when the database does not exist in the current data source`(
        project: Project,
        psiFile: PsiFile,
        fixture: CodeInsightTestFixture,
    ) {
        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDialect(JavaDriverDialect)

        `when`(readModelProvider.slice(eq(dataSource), eq(ListDatabases.Slice))).thenReturn(
            ListDatabases(emptyList())
        )

        fixture.enableInspections(NamespaceCheckInspectionBridge::class.java)
        fixture.testHighlighting()
    }

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
        return client.getDatabase("myDatabase")
                .getCollection(<warning descr="Cannot resolve \"myCollection\" collection in \"myDatabase\" database in the connected data source.">"myCollection"</warning>)
                .find(eq("nonExistingField", "123"));
    }
}
        """,
    )
    fun `shows an inspection when the collection does not exist in the current data source`(
        project: Project,
        psiFile: PsiFile,
        fixture: CodeInsightTestFixture,
    ) {
        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDialect(JavaDriverDialect)

        `when`(readModelProvider.slice(eq(dataSource), eq(ListDatabases.Slice))).thenReturn(
            ListDatabases(listOf(ListDatabases.Database("myDatabase")))
        )

        `when`(readModelProvider.slice(eq(dataSource), any<ListCollections.Slice>())).thenReturn(
            ListCollections(emptyList())
        )

        fixture.enableInspections(NamespaceCheckInspectionBridge::class.java)
        fixture.testHighlighting()
    }
}
