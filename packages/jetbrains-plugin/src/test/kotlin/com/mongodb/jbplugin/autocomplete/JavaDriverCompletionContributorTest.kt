package com.mongodb.jbplugin.autocomplete

import com.intellij.database.dataSource.DatabaseConnectionManager
import com.intellij.database.dataSource.DatabaseConnectionPoint
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.dataSource.localDataSource
import com.intellij.database.psi.DbDataSource
import com.intellij.database.psi.DbPsiFacade
import com.intellij.database.util.common.containsElements
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.mongodb.jbplugin.accessadapter.datagrip.DataGripBasedReadModelProvider
import com.mongodb.jbplugin.accessadapter.slice.GetCollectionSchema
import com.mongodb.jbplugin.accessadapter.slice.ListCollections
import com.mongodb.jbplugin.accessadapter.slice.ListDatabases
import com.mongodb.jbplugin.editor.MongoDbVirtualFileDataSourceProvider
import com.mongodb.jbplugin.fixtures.*
import com.mongodb.jbplugin.fixtures.mockDataSource
import com.mongodb.jbplugin.fixtures.mockDatabaseConnection
import com.mongodb.jbplugin.mql.BsonObject
import com.mongodb.jbplugin.mql.BsonString
import com.mongodb.jbplugin.mql.CollectionSchema
import com.mongodb.jbplugin.mql.Namespace
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.eq

@CodeInsightTest
class JavaDriverCompletionContributorTest {
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
        return client.getDatabase(<caret>)
    }
}
        """,
    )
    fun `should autocomplete databases from the current connection`(
        project: Project,
        psiFile: PsiFile,
        fixture: CodeInsightTestFixture,
    ) {
        val (dataSource, readModelProvider) = setupConnection(fixture, project)

        `when`(readModelProvider.slice(eq(dataSource), any<ListDatabases.Slice>())).thenReturn(
            ListDatabases(
                listOf(
                    ListDatabases.Database("myDatabase1"),
                    ListDatabases.Database("myDatabase2"),
                ),
            ),
        )

        val elements = fixture.completeBasic()

        assertTrue(
            elements.containsElements {
                it.lookupString == "myDatabase1"
            },
        )
        assertTrue(
            elements.containsElements {
                it.lookupString == "myDatabase2"
            },
        )
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
        return client.getDatabase("myDatabase").getCollection("<caret>").find();
    }
}
        """,
    )
    fun `should autocomplete collections from the current connection and inferred database`(
        project: Project,
        psiFile: PsiFile,
        fixture: CodeInsightTestFixture,
    ) {
        val (dataSource, readModelProvider) = setupConnection(fixture, project)

        `when`(readModelProvider.slice(eq(dataSource), eq(ListCollections.Slice("myDatabase")))).thenReturn(
            ListCollections(
                listOf(
                    ListCollections.Collection("myCollection", "collection"),
                ),
            ),
        )

        val elements = fixture.completeBasic()

        assertTrue(
            elements.containsElements {
                it.lookupString == "myCollection"
            },
        )
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
        return client.getDatabase("myDatabase").getCollection("myCollection")
                .find(eq("<caret>"));
    }
}
        """,
    )
    fun `should autocomplete fields from the current namespace`(
        project: Project,
        psiFile: PsiFile,
        fixture: CodeInsightTestFixture,
    ) {
        val (dataSource, readModelProvider) = setupConnection(fixture, project)
        val namespace = Namespace("myDatabase", "myCollection")

        `when`(readModelProvider.slice(eq(dataSource), eq(GetCollectionSchema.Slice(namespace)))).thenReturn(
            GetCollectionSchema(
                CollectionSchema(
                    namespace,
                    BsonObject(
                        mapOf(
                            "myField" to BsonString,
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
import static com.mongodb.client.model.Updates.*;

public class Repository {
    private final MongoClient client;

    public Repository(MongoClient client) {
        this.client = client;
    }

    public void exampleFind() {
        client.getDatabase("myDatabase").getCollection("myCollection")
                .updateMany(eq("<caret>"), set("x", 1));
    }
}
        """,
    )
    fun `should autocomplete fields from the current namespace in the filters of an update`(
        project: Project,
        psiFile: PsiFile,
        fixture: CodeInsightTestFixture,
    ) {
        val (dataSource, readModelProvider) = setupConnection(fixture, project)
        val namespace = Namespace("myDatabase", "myCollection")

        `when`(readModelProvider.slice(eq(dataSource), eq(GetCollectionSchema.Slice(namespace)))).thenReturn(
            GetCollectionSchema(
                CollectionSchema(
                    namespace,
                    BsonObject(
                        mapOf(
                            "myField" to BsonString,
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
import static com.mongodb.client.model.Updates.*;

public class Repository {
    private final MongoClient client;

    public Repository(MongoClient client) {
        this.client = client;
    }

    public void exampleFind() {
        client.getDatabase("myDatabase").getCollection("myCollection")
                .updateMany(eq("x", 1), set("<caret>", 2));
    }
}
        """,
    )
    fun `should autocomplete fields from the current namespace in the updates of an update`(
        project: Project,
        psiFile: PsiFile,
        fixture: CodeInsightTestFixture,
    ) {
        val (dataSource, readModelProvider) = setupConnection(fixture, project)
        val namespace = Namespace("myDatabase", "myCollection")

        `when`(readModelProvider.slice(eq(dataSource), eq(GetCollectionSchema.Slice(namespace)))).thenReturn(
            GetCollectionSchema(
                CollectionSchema(
                    namespace,
                    BsonObject(
                        mapOf(
                            "myField" to BsonString,
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
    }

    private fun setupConnection(
        fixture: CodeInsightTestFixture,
        project: Project,
    ): Pair<LocalDataSource, DataGripBasedReadModelProvider> {
        val dbPsiFacade = mock<DbPsiFacade>()
        val dbDataSource = mock<DbDataSource>()
        val dataSource = mockDataSource()
        val application = ApplicationManager.getApplication()
        val realConnectionManager = DatabaseConnectionManager.getInstance()
        val dbConnectionManager =
            mock<DatabaseConnectionManager>().also { cm ->
                `when`(cm.build(any(), any())).thenAnswer {
                    realConnectionManager.build(it.arguments[0] as Project, it.arguments[1] as DatabaseConnectionPoint)
                }
            }
        val connection = mockDatabaseConnection(dataSource)
        val readModelProvider = mock<DataGripBasedReadModelProvider>()

        `when`(dbDataSource.localDataSource).thenReturn(dataSource)
        `when`(dbPsiFacade.findDataSource(any())).thenReturn(dbDataSource)
        `when`(dbConnectionManager.activeConnections).thenReturn(listOf(connection))

        fixture.file.virtualFile.putUserData(
            MongoDbVirtualFileDataSourceProvider.Keys.attachedDataSource,
            dataSource,
        )

        application.withMockedService(dbConnectionManager)
        project.withMockedService(readModelProvider)
        project.withMockedService(dbPsiFacade)

        return Pair(dataSource, readModelProvider)
    }
}
